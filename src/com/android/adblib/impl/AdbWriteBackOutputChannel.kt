/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.impl

import com.android.adblib.AdbBufferedOutputChannel
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbPipedInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

/**
 * Implementation of an [AdbOutputChannel] that buffers write operations to an in-memory
 * [AdbPipedInputChannel], and uses a "write-back" coroutine to flush the in-memory
 * [AdbPipedInputChannel] contents to [output].
 */
internal class AdbWriteBackOutputChannel(
    session: AdbSession,
    private val output: AdbOutputChannel,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) : AdbBufferedOutputChannel {

    private val logger = thisLogger(session)

    /**
     * The [AdbPipedInputChannel] used to concurrently store write-back from [write] operations.
     */
    private val pipe = session.channelFactory.createPipedChannel(bufferSize)

    private val writeBackWorker = WriteBackWorker(session, pipe, output, bufferSize)

    override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        // We use the "write-back" context to ensure any write failure in the "write-back"
        // coroutine cancels this "write" operation with the "write-back" failure.
        return writeBackWorker.withWriteBackContext {
            pipe.pipeSource.write(buffer, timeout, unit).also {
                logger.verbose { "write: Wrote $it bytes to in-memory pipe" }
            }
        }
    }

    override suspend fun shutdown() {
        logger.debug { "Shutting down write-back output channel" }
        pipe.pipeSource.close() // Signal we are done writing
        writeBackWorker.join() // Wait for "write-back" coroutine to finish all writes
        output.close() // Close wrapped output channel since we are done writing
    }

    override fun close() {
        logger.debug { "Closing write-back output channel" }
        writeBackWorker.close()
        pipe.pipeSource.close()
        pipe.close()
        output.close()
    }

    /**
     * Handles the "write-back" coroutine: writes bytes from [input] to [output] as fast
     * as possible.
     */
    class WriteBackWorker(
        session: AdbSession,
        private val input: AdbInputChannel,
        private val output: AdbOutputChannel,
        bufferSize: Int
    ) : AutoCloseable {

        private val logger = thisLogger(session)

        /**
         * The scope of the write-back coroutine.
         */
        private val scope = session.scope.createChildScope(isSupervisor = false)

        /**
         * The [Job] where [writeBack] is launched.
         */
        private val job: Job

        init {
            // We use a custom exception handler to prevent exception from the "write back"
            // coroutine to leak to the parent scope. Note that we still rely on the
            // launched [job] to keep its "exception" state when using [withWriteBackContext].
            val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
                logger.debug { "Exception in \"write-back\" coroutine '$throwable'" }
            }

            job = scope.launch(exceptionHandler) {
                // Note: [writeBack] may throw an exception if it fails to write
                // to the output channel. That exception will cancel this scope
                // with the original exception as the cause of cancellation.
                writeBack(bufferSize)
            }
        }

        suspend fun join() {
            // We use the "write-back" context to ensure any write failure in the
            // "write-back" coroutine is rethrown to the caller of this method.
            withWriteBackContext {
                job.join()
            }
        }

        /**
         * Executes [block] in the context of [scope], so that [block] is cancelled
         * right away if the [writeBack] coroutine is cancelled, e.g. due to a write error.
         *
         * Note: This method rethrows the same exception that occurred in [writeBack] if
         * the write-back coroutine had a failure.
         */
        suspend fun <R> withWriteBackContext(block: suspend () -> R): R {
            return try {
                withContext(scope.coroutineContext) {
                    block()
                }
            } catch (t: Throwable) {
                logger.verbose(t) { "Error in write-back context: '$t'" }
                // If we get cancellation here, it probably means the [writeBack] coroutine
                // got an exception when writing to the [output]. That original exception
                // is stored in the first "non cancellation" exception in the "cause" chain.
                if (job.isCancelled && (t is CancellationException)) {
                    t.firstCauseOrNull { it !is CancellationException }?.let { throw it }
                }
                throw t
            }
        }

        private suspend fun writeBack(bufferSize: Int) {
            logger.debug { "write-back coroutine starting with bufferSize=$bufferSize" }
            val buffer = ByteBuffer.allocate(bufferSize)
            while (true) {
                buffer.clear() // [position=0, limit=capacity]
                val byteCount = input.read(buffer) // [position=byteCount, limit=capacity]
                logger.verbose { "$byteCount byte(s) read from input '$input'" }
                if (byteCount < 0) {
                    // Reached EOF, we are done
                    break
                }
                buffer.flip() // [position=byteCount, limit=capacity] -> [position=0, limit]
                output.writeExactly(buffer)
                logger.verbose { "$byteCount bytes forwarded to output '$output'" }
            }
        }

        private inline fun Throwable.firstCauseOrNull(predicate: (Throwable) -> Boolean): Throwable? {
            var cause = this
            repeat(15) {
                if (predicate(cause)) {
                    return cause
                }
                cause = cause.cause ?: return null
            }
            return null
        }

        override fun close() {
            scope.cancel("Write back output closed")
        }
    }
}
