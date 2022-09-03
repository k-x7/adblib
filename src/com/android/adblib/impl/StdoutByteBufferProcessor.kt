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

import java.nio.ByteBuffer

/**
 * Support for optional filtering of output of [AdbDeviceServicesImpl.shell].
 */
internal abstract class StdoutByteBufferProcessor {
    abstract fun convertBuffer(buffer: ByteBuffer): ByteBuffer
    abstract fun convertBufferEnd(): ByteBuffer?

    /**
     * A [StdoutByteBufferProcessor] that emits [ByteBuffer] without touching their contents.
     */
    internal class DirectProcessor : StdoutByteBufferProcessor() {
        override fun convertBuffer(buffer: ByteBuffer) = buffer
        override fun convertBufferEnd(): ByteBuffer? = null
    }

    /**
     * A [StdoutByteBufferProcessor] that emits [ByteBuffer] after converting all '\r\n'
     * occurrences to '\n'.
     */
    internal class StripCrLfProcessor : StdoutByteBufferProcessor() {

        private var workBuffer: ByteBuffer? = null
        private var leftoverByte: Byte? = null

        override fun convertBuffer(buffer: ByteBuffer): ByteBuffer {
            // Skip empty buffer so that we can ignore that corner case later on
            if (buffer.remaining() == 0) {
                return buffer
            }

            val destinationBuffer = ensureWorkBuffer(buffer)
            destinationBuffer.clear()

            // If there was a leftover byte, we have to check the first character for '\n'
            leftoverByte?.let { byte ->
                assert(buffer.remaining() > 0)
                if (byte == '\r'.code.toByte() && buffer.get(buffer.position()) == '\n'.code.toByte()) {
                    // We have '\r\n', so we have nothing to do (just leave '\n')
                } else {
                    // We don't have '\r\n', but we have a leftover character, so add it
                    destinationBuffer.put(byte)
                }
                leftoverByte = null
            }

            // We copy 'buffer' into 'destinationBuffer' in one pass, replacing '\r\n' with '\n'.
            // Buffer has data from [position, limit[
            val slice = buffer.slice()
            processCrLfRanges(buffer) { start, end ->
                // If last character is a '\r' at the end, skip it and mark it for later
                val actualEnd = if (end == buffer.limit() && (start < end) && (buffer.get(end - 1) == '\r'.code.toByte())) {
                    leftoverByte = '\r'.code.toByte()
                    end - 1
                } else if (end == buffer.limit() && destinationBuffer.remaining() < (end - start)) {
                    assert(destinationBuffer.remaining() == (end - start - 1))
                    leftoverByte = buffer.get(end - 1)
                    end - 1
                } else {
                    end
                }
                slice.limit(actualEnd)
                slice.position(start)
                destinationBuffer.put(slice)
            }
            buffer.position(buffer.limit())

            // [0, position[ => [position, limit[
            destinationBuffer.flip()
            return destinationBuffer
        }

        override fun convertBufferEnd(): ByteBuffer? {
            return leftoverByte?.let { byte ->
                workBuffer?.let { buffer ->
                    buffer.clear()
                    buffer.put(byte)
                    buffer.flip()
                }
                workBuffer
            }
        }


        private fun ensureWorkBuffer(buffer: ByteBuffer): ByteBuffer {
            return workBuffer?.also {
                if (it.capacity() != buffer.capacity()) {
                    throw IllegalArgumentException("ByteBuffer capacity should not vary during successive calls")
                }
            } ?: ByteBuffer.allocate(buffer.capacity()).also { workBuffer = it }
        }

        /**
         * Scan [buffer] for '\r\n' characters, invoking [block] for each sub-range
         * that does not contain the '\r' character.
         *
         *     0    1    2    3    4    5    6    7    8    9
         *     a  | b  | c  | \r | \n | d  | e  | \r | \n | f
         *                    x                   x
         *
         *     block(0, 3) // start offset, end offset (exclusive)
         *     block(4, 7)
         *     block(8, 9)
         */
        private fun processCrLfRanges(buffer: ByteBuffer, block: (Int, Int) -> Unit) {
            var index = buffer.position()
            val limit = buffer.limit()
            while (index < limit) {
                val crLfOffset = indexOfCrLf(buffer, index, limit)
                if (crLfOffset < 0) {
                    block(index, limit)
                    break
                }
                block(index, crLfOffset)
                index = crLfOffset + 1 // SKip '\r'
            }
        }

        private fun indexOfCrLf(bytes: ByteBuffer, offset: Int, limit: Int): Int {
            var previousByte: Byte = -1
            for (index in offset until limit) {
                val currentByte = bytes.get(index)
                // Do we have '\r\n' at "index"?
                //TODO: There should be a more efficient way to look for bytes in a ByteBuffer.
                if ((previousByte == '\r'.code.toByte()) && (currentByte == '\n'.code.toByte())) {
                    return index - 1
                }
                previousByte = currentByte
            }
            return -1
        }
    }
}
