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
package com.android.adblib

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.InterruptedIOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

/**
 * An [AdbInputChannel] that can be fed data in a thread-safe way from
 * an [AdbPipedOutputChannel], i.e. write operation to the [AdbPipedOutputChannel]
 * end up feeding pending (or future) [read] operations on this [AdbPipedInputChannelImpl].
 *
 * @see java.io.PipedInputStream
 * @see java.io.PipedOutputStream
 */
internal class AdbPipedInputChannelImpl(private val session: AdbSession, bufferSize: Int = 4_096) :
    AdbPipedInputChannel {

    private val logger = thisLogger(session)

    /**
     * TODO: Replace [PipedOutputStream] implementation with a coroutine based
     *       implementation to avoid issues with blocking thread I/O operations
     *       that are non-cancellable (and inefficient use of threads).
     */
    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream, bufferSize)
    private val readBytes = ByteArray(bufferSize)
    private val receiveBytes = ByteArray(bufferSize)

    override val pipeSource: AdbOutputChannel = AdbPipedOutputChannel(session, this)

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return withTimeout(unit.toMillis(timeout)) {
            withContext(session.blockingIoDispatcher) {
                val bytesToRead = min(buffer.remaining(), readBytes.size)
                if (bytesToRead == 0) {
                    0
                } else {
                    // Note: "runInterruptible" ensures the blocking I/O is interrupted
                    // by a "Thread.interrupt()" call if this read operation is cancelled
                    // (e.g. by a timeout or by a parent job cancellation).
                    val byteCount = runInterruptibleIO {
                        inputStream.read(readBytes, 0, bytesToRead)
                    }
                    if (byteCount > 0) {
                        buffer.put(readBytes, 0, byteCount)
                    }
                    logger.verbose { "read(): $byteCount bytes read" }
                    byteCount
                }
            }
        }
    }

    override fun close() {
        logger.debug { "close()" }
        inputStream.close()
    }

    fun closeWriter() {
        logger.debug { "closeWriter()" }
        outputStream.close()
    }

    suspend fun receive(buffer: ByteBuffer): Int {
        logger.verbose { "receive(${buffer.remaining()})" }
        return withContext(session.blockingIoDispatcher) {
            val byteCount = min(buffer.remaining(), receiveBytes.size)
            if (byteCount > 0) {
                // Move buffer data to intermediate buffer for sending to output stream
                buffer.get(receiveBytes, 0, byteCount)

                // Note: "runInterruptible" ensures the blocking I/O is interrupted
                // by a "Thread.interrupt()" call if this read operation is cancelled
                // (e.g. by a timeout or by a parent job cancellation).
                runInterruptibleIO {
                    outputStream.write(receiveBytes, 0, byteCount)
                }
            }
            logger.verbose { "receive(): $byteCount bytes received" }
            byteCount
        }
    }

    /**
     * Similar to [runInterruptible], but handles [InterruptedIOException] in addition to
     * [InterruptedException].
     */
    private suspend fun <T> runInterruptibleIO(
        context: CoroutineContext = EmptyCoroutineContext,
        block: () -> T
    ): T {
        return try {
            runInterruptible(context, block)
        } catch (e: InterruptedIOException) {
            throw CancellationException("Blocking call was interrupted due to parent cancellation").initCause(
                e
            )
        }
    }

    private class AdbPipedOutputChannel(
        session: AdbSession,
        val input: AdbPipedInputChannelImpl
    ) : AdbOutputChannel {

        private val logger = thisLogger(session)

        override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
            logger.verbose { "write(${buffer.remaining()})" }
            return withTimeout(unit.toMillis(timeout)) {
                input.receive(buffer)
            }
        }

        override fun close() {
            logger.debug { "close()" }
            input.closeWriter()
        }
    }
}
