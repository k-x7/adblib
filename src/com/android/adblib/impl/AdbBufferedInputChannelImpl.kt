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

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import java.io.BufferedInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Implementation of an [AdbInputChannel] that reads data from another [AdbInputChannel]
 * using an internal [ByteBuffer] of the given buffer size.
 *
 * This class is similar to [BufferedInputStream], but for [AdbInputChannel] instead of
 * [InputStream].
 */
internal class AdbBufferedInputChannelImpl(
  session: AdbSession,
  private val input: AdbInputChannel,
  bufferSize: Int = DEFAULT_BUFFER_SIZE
) : AdbInputChannel {

    private val logger = thisLogger(session)

    /**
     * The bytes that were read from [input], starting at position `0` up to position
     * [ByteBuffer.limit].
     */
    private val inputBuffer = ByteBuffer.allocate(bufferSize).limit(0)

    /**
     * [ByteBuffer.slice] of [inputBuffer] that contains bytes available for the next [read]
     * operations(s), starting at [ByteBuffer.position] up to [ByteBuffer.limit].
     *
     * Note: [inputBuffer] and [inputBufferSlice] always have the same [ByteBuffer.limit]
     * and [ByteBuffer.capacity].
     */
    private val inputBufferSlice = inputBuffer.duplicate()

    init {
        assertInputBufferIsValid()
    }

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        // Fast path: internal buffer contains data
        if (inputBufferSlice.remaining() > 0) {
            return copyInputBufferTo(buffer).also { count ->
                logger.verbose { "read: Copied $count bytes from input '$input'" }
            }
        }
        return readAndCopyInputBufferTo(buffer, timeout, unit)
    }

    override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        // Fast path: internal buffer contains enough data to fill [buffer]
        if (inputBufferSlice.remaining() >= buffer.remaining()) {
            copyInputBufferTo(buffer).also { count ->
                logger.verbose { "readExactly: Copied $count bytes from input '$input'" }
            }
            assert(buffer.remaining() == 0)
            return
        }
        super.readExactly(buffer, timeout, unit)
    }

    private fun copyInputBufferTo(buffer: ByteBuffer): Int {
        val count = min(inputBufferSlice.remaining(), buffer.remaining())
        val savedLimit = inputBufferSlice.limit()
        inputBufferSlice.limit(inputBufferSlice.position() + count)
        buffer.put(inputBufferSlice)
        inputBufferSlice.limit(savedLimit)
        return count
    }

    private suspend fun readAndCopyInputBufferTo(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        assertInputBufferIsValid()
        inputBuffer.clear()
        inputBufferSlice.clear()
        val count = input.read(inputBuffer, timeout, unit)
        logger.verbose { "read: Read $count bytes from input '$input'" }
        if (count <= 0) {
            return count
        }
        inputBuffer.flip()
        inputBufferSlice.limit(count)
        assertInputBufferIsValid()
        assert(inputBuffer.remaining() > 0)
        assert(inputBufferSlice.remaining() > 0)
        assert(inputBufferSlice.remaining() == inputBuffer.remaining())
        return copyInputBufferTo(buffer)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun assertInputBufferIsValid() {
        assert(inputBuffer.position() <= inputBufferSlice.position())
        assert(inputBuffer.limit() == inputBufferSlice.limit())
        assert(inputBuffer.capacity() == inputBufferSlice.capacity())
    }

    override fun close() {
        input.close()
    }
}
