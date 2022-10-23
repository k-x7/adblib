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

import com.android.adblib.AdbPipedInputChannel
import com.android.adblib.AdbPipedOutputChannel
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import com.android.adblib.utils.CircularByteBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

internal class AdbPipedInputChannelImpl(
    session: AdbSession,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) : AdbPipedInputChannel {

    private val logger = thisLogger(session)

    /**
     * Guard access to [circularBuffer]
     */
    private val circularBufferLock = Any()

    /**
     * The circular buffer used to store bytes for [read] and [receive] operations
     */
    private val circularBuffer = CircularByteBuffer(bufferSize)

    /**
     * Lock to use when updating the value of [state]
     */
    private val stateLock = Any()

    /**
     * [StateFlow] of [State] used to coordinate calls to [read], [receive], [close] and
     * [closeWriter]. The intent is to make sure any state change will wake up a pending
     * [read] or [receive] waiting on the [StateFlow].
     */
    private val state = MutableStateFlow(
        State(
            receivedBytes = circularBuffer.size,
            freeBytes = circularBuffer.remaining,
            closed = false,
            pipeSourceClosed = false,
            pipeSourceError = null
        )
    )

    override val pipeSource: AdbPipedOutputChannel = AdbPipedOutputChannelImpl(session, this)

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return withTimeout(unit.toMillis(timeout)) {
            readImpl(buffer)
        }
    }

    override fun toString(): String {
        return "AdbPipeInputChannel(id=${this.hashCode()}, state=${state.value})"
    }

    private suspend fun readImpl(buffer: ByteBuffer): Int {
        logger.verbose { "read(${buffer.remaining()})" }
        while (true) {
            synchronized(circularBufferLock) {
                // If close has been called, fail immediately
                if (state.value.closed) {
                    throw ClosedChannelException()
                }
                // No-op if empty target buffer
                if (buffer.remaining() == 0) {
                    return 0
                }
                // Move bytes from the circular buffer to the target buffer
                val byteCount = circularBuffer.read(buffer)
                updateState {
                    it.copy(
                        receivedBytes = circularBuffer.size,
                        freeBytes = circularBuffer.remaining
                    )
                }
                if (byteCount > 0) {
                    logger.verbose { "read: $byteCount bytes read" }
                    return byteCount
                }

                // If output had an error and there are no available bytes, rethrow error
                state.value.pipeSourceError?.also {
                    logger.verbose { "read(): error from pipe source: '$it'" }
                    throw it
                }

                // If output has closed and there are no available bytes, we reached EOF
                if (state.value.pipeSourceClosed) {
                    logger.verbose { "read(): EOF reached" }
                    return -1
                }
            }

            // Wait until bytes are received, or close is called
            state.waitUntil { closed || pipeSourceClosed || pipeSourceError != null || receivedBytes > 0 }
        }
    }

    override fun close() {
        logger.debug { "close()" }
        updateState {
            it.copy(closed = true)
        }
    }

    /**
     * Appends the contents of [sourceBuffer] to the list of bytes available for [read].
     * Suspends until there is enough room in the internal buffer to write at least one byte.
     */
    private suspend fun receive(sourceBuffer: ByteBuffer): Int {
        logger.verbose { "receive(${sourceBuffer.remaining()})" }
        while (true) {
            synchronized(circularBufferLock) {
                // If "closeWriter" (or "close") has been called, fail immediately
                if (state.value.closed || state.value.pipeSourceClosed || state.value.pipeSourceError != null) {
                    throw ClosedChannelException()
                }
                // No-op if empty source buffer
                if (sourceBuffer.remaining() == 0) {
                    return 0
                }
                // Move bytes from the source buffer to the circular buffer
                val byteCount = circularBuffer.add(sourceBuffer)
                updateState {
                    it.copy(
                        receivedBytes = circularBuffer.size,
                        freeBytes = circularBuffer.remaining
                    )
                }
                if (byteCount > 0) {
                    logger.verbose { "receive(): $byteCount bytes received" }
                    return byteCount
                }
            }

            // There was no room in circular buffer, wait until close is called
            // or a "read" operation frees up room from the circular buffer.
            state.waitUntil { closed || pipeSourceClosed || pipeSourceError != null || freeBytes > 0 }
        }
    }

    /**
     * Notify an error was emitted from [pipeSource]
     */
    private fun receiveError(throwable: Throwable) {
        logger.verbose { "receiveError($throwable)" }
        updateState { it.copy(pipeSourceError = throwable) }
    }

    /**
     * Notification from the associated [AdbPipedOutputChannel] that is has been closed,
     * i.e. no more bytes will be sent to [receive].
     */
    private fun closeWriter() {
        logger.debug { "closeWriter()" }
        updateState { it.copy(pipeSourceClosed = true) }
    }

    private fun updateState(block: (State) -> State) {
        synchronized(stateLock) {
            state.value = block(state.value)
        }
    }

    private suspend fun <T> StateFlow<T>.waitUntil(predicate: T.() -> Boolean) {
        if (!value.predicate()) {
            first { it.predicate() }
        }
    }

    private class AdbPipedOutputChannelImpl(
        session: AdbSession,
        val input: AdbPipedInputChannelImpl
    ) : AdbPipedOutputChannel {

        private val logger = thisLogger(session)

        override suspend fun error(throwable: Throwable) {
            logger.verbose { "error($throwable)" }
            input.receiveError(throwable)
        }

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

    private data class State(
        /**
         * The number of in-use bytes in [circularBuffer] (i.e. [CircularByteBuffer.size])
         */
        val receivedBytes: Int,
        /**
         * The number of unused bytes in [circularBuffer] (i.e. [CircularByteBuffer.remaining])
         */
        val freeBytes: Int,
        /**
         * Whether [close] has been called
         */
        val closed: Boolean,
        /**
         * Whether [closeWriter] has been called
         */
        val pipeSourceClosed: Boolean,
        /**
         * Error reported from [AdbPipedOutputChannel.error]
         */
        val pipeSourceError: Throwable?)
}
