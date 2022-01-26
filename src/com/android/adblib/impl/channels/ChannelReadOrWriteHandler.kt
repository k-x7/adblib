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
package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.thisLogger
import kotlinx.coroutines.CancellableContinuation
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Allows calling read/writes methods of an NIO [Channel] that require a [CompletionHandler] as
 * Kotlin coroutines.
 *
 * Note: Do not instantiate or inherit from this class directly, instead use [ChannelReadHandler]
 * or [ChannelWriteHandler] for read or write operations.
 *
 * @see ChannelReadHandler
 * @see ChannelWriteHandler
 * @see ContinuationCompletionHandler
 * @see java.nio.channels.AsynchronousFileChannel
 * @see java.nio.channels.AsynchronousSocketChannel
 * @see java.nio.channels.CompletionHandler
 */
internal abstract class ChannelReadOrWriteHandler protected constructor(
    private val host: AdbSessionHost,
    private val nioChannel: Channel
) {

    private val logger = thisLogger(host)

    private val completionHandler = object : ContinuationCompletionHandler<Int>() {
        override fun completed(result: Int, continuation: CancellableContinuation<Int>) {
            completionHandlerCompleted(result, continuation)
        }
    }

    /**
     * The [ByteBuffer] used when [runExactly] is active
     */
    private var buffer: ByteBuffer? = null

    /**
     * The [TimeoutTracker] used when [runExactly] is active
     */
    private var timeoutTracker: TimeoutTracker? = null

    /**
     * `true` if the [asyncReadOrWrite] implementation natively supports timeouts,
     * `false` if the timeout handling should be done by this base class
     *
     * @see suspendChannelCoroutine
     */
    protected abstract val supportsTimeout: Boolean

    /**
     * Invokes a single asynchronous `read` or `write` operation on the underlying [nioChannel]
     */
    protected abstract fun asyncReadOrWrite(
        buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit,
        continuation: CancellableContinuation<Int>,
        completionHandler: ContinuationCompletionHandler<Int>
    )

    /**
     * Invoked when a single [asyncReadOrWrite] successfully completes, passing the number of bytes
     * read/written as [byteCount]
     */
    protected abstract fun asyncReadOrWriteCompleted(byteCount: Int)

    protected suspend fun run(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        // Special case of 0 bytes
        if (!buffer.hasRemaining()) {
            return 0
        }

        return if (supportsTimeout) {
            suspendChannelCoroutine(host, nioChannel) { continuation ->
                asyncReadOrWrite(buffer, timeout, unit, continuation, completionHandler)
            }
        } else {
            suspendChannelCoroutine(host, nioChannel, timeout, unit) { continuation ->
                asyncReadOrWrite(buffer, timeout, unit, continuation, completionHandler)
            }
        }
    }

    protected suspend fun runExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        runExactlyBegin(buffer, timeout, unit)
        try {
            run(buffer, timeout, unit)
        } finally {
            runExactlyEnd()
        }
    }

    private fun runExactlyBegin(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        if (this.buffer != null) {
            throw IllegalStateException("An Async I/O operation is still pending")
        }

        this.buffer = buffer
        this.timeoutTracker = TimeoutTracker.fromTimeout(unit, timeout)
    }

    private fun runExactlyEnd() {
        buffer = null
        timeoutTracker = null
    }

     private fun completionHandlerCompleted(result: Int, continuation: CancellableContinuation<Int>) {
        try {
            logger.verbose { "Async I/O operation completed successfully ($result bytes)" }

            return if (buffer == null) {
                // Not a "runExactly" completion: complete right away
                finalCompletionCompleted(result, continuation)
            } else {
                // A "runExactly" completion: start another async operation
                // if the buffer is not fully processed
                runExactlyCompleted(result, continuation)
            }
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    private fun finalCompletionCompleted(result: Int, continuation: CancellableContinuation<Int>) {
        try {
            asyncReadOrWriteCompleted(result)
        } finally {
            continuation.resume(result)
        }
    }

    private fun runExactlyCompleted(result: Int, continuation: CancellableContinuation<Int>) {
        val tempBuffer = buffer ?: internalError("buffer is null")
        if (tempBuffer.remaining() == 0) {
            return finalCompletionCompleted(result, continuation)
        }

        // EOF, stop reading more (-1 never happens with a "write" operation)
        if (result == -1) {
            logger.verbose { "Reached EOF" }
            continuation.resumeWithException(EOFException("Unexpected end of asynchronous channel"))
            return
        }

        // One more async read/write since buffer is not full and channel is not EOF
        asyncReadOrWriteCompleted(result)

        val tempTimeoutTracker = timeoutTracker ?: internalError("timeout tracker is null")
        return asyncReadOrWrite(
            tempBuffer,
            tempTimeoutTracker.remainingMills,
            TimeUnit.MILLISECONDS,
            continuation,
            completionHandler
        )
    }

    private fun internalError(message: String): Nothing {
        val error = IllegalStateException("Internal error during Async I/O: $message")
        logger.error(error, message)
        throw error
    }
}

/**
 * Provides services to [read] data from any NIO [Channel] that supports asynchronous
 * reads, e.g. [AsynchronousFileChannel] or [AsynchronousSocketChannel].
 */
internal abstract class ChannelReadHandler(
    host: AdbSessionHost,
    nioChannel: Channel
) : ChannelReadOrWriteHandler(host, nioChannel) {

    /**
     * Reads up to [ByteBuffer.remaining] bytes from the underlying channel, returning -1
     * when EOF is reached.
     *
     * @see AdbInputChannel.read
     */
    suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return run(buffer, timeout, unit)
    }

    /**
     * Reads exactly [ByteBuffer.remaining] bytes from the underlying channel, throwing an
     * [EOFException] if the channel reaches EOF.
     *
     * @see AdbInputChannel.readExactly
     */
    suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        runExactly(buffer, timeout, unit)
    }

    override fun asyncReadOrWrite(
        buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit,
        continuation: CancellableContinuation<Int>,
        completionHandler: ContinuationCompletionHandler<Int>
    ) {
        asyncRead(buffer, timeout, unit, continuation, completionHandler)
    }

    override fun asyncReadOrWriteCompleted(byteCount: Int) {
        asyncReadCompleted(byteCount)
    }

    protected abstract fun asyncRead(
        buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit,
        continuation: CancellableContinuation<Int>,
        completionHandler: ContinuationCompletionHandler<Int>
    )

    protected open fun asyncReadCompleted(byteCount: Int) {
    }
}

/**
 * Provides services to [write] data from any NIO [Channel] that supports asynchronous
 * writes, e.g. [AsynchronousFileChannel] or [AsynchronousSocketChannel].
 */
internal abstract class ChannelWriteHandler(
    host: AdbSessionHost,
    nioChannel: Channel
) : ChannelReadOrWriteHandler(host, nioChannel) {

    /**
     * Writes up to [ByteBuffer.remaining] bytes to the underlying channel, returning
     * the number of bytes successfully written.
     *
     * @see AdbOutputChannel.write
     */
    suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return run(buffer, timeout, unit)
    }

    /**
     * Writes exactly [ByteBuffer.remaining] bytes to the underlying channel.
     *
     * @see AdbOutputChannel.writeExactly
     */
    suspend fun writeExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        runExactly(buffer, timeout, unit)
    }

    override fun asyncReadOrWrite(
        buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit,
        continuation: CancellableContinuation<Int>,
        completionHandler: ContinuationCompletionHandler<Int>
    ) {
        asyncWrite(buffer, timeout, unit, continuation, completionHandler)
    }

    override fun asyncReadOrWriteCompleted(byteCount: Int) {
        asyncWriteCompleted(byteCount)
    }

    protected abstract fun asyncWrite(
        buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit,
        continuation: CancellableContinuation<Int>,
        completionHandler: ContinuationCompletionHandler<Int>
    )

    protected open fun asyncWriteCompleted(byteCount: Int) {
    }
}
