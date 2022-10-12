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
package com.android.adblib.utils

import java.nio.ByteBuffer
import kotlin.math.min

/**
 * A fixed size circular buffer of bytes, i.e. a wrapper over a fixed size [ByteArray]
 * than allows [adding][add] bytes "to the end" and [reading][read] bytes "from the start"
 * efficiently (i.e. without moving bytes in the array).
 *
 * @param size the size of buffer to create
 */
class CircularByteBuffer(size: Int = DEFAULT_BUFFER_SIZE) {

    /**
     * The circular buffer data
     */
    private val buffer = ByteArray(size)

    /**
     * Index of the first byte of available data in the buffer
     */
    private var startOffset: Int = 0

    /**
     * Index of the first byte of free space in the buffer (also index of last byte of
     * available data).
     */
    private var endOffset: Int = 0

    /**
     * The number of bytes that currently in use.
     */
    var size: Int = 0
        private set

    /**
     * The number of unused bytes, ranging from `zero`, when the buffer is full, to
     * [capacity], when buffer is empty.
     */
    val remaining: Int
        get() = buffer.size - size

    /**
     * The maximum number of bytes the buffer can store.
     */
    val capacity: Int
        get() = buffer.size

    /**
     * Removes all bytes from the buffer.
     */
    fun clear() {
        startOffset = 0
        endOffset = 0
        size = 0
    }

    /**
     * Appends bytes from [sourceBuffer] to the end of this [CircularByteBuffer], returning
     * the number of bytes copied.
     *
     * * If there is not enough [remaining] capacity in this [CircularByteBuffer], only a
     * subset of [sourceBuffer] is copied over. In particular, if [CircularByteBuffer] is full,
     * this method returns `zero` and no bytes are copied from [sourceBuffer].
     */
    fun add(sourceBuffer: ByteBuffer): Int {
        val copyLength = min(remaining, sourceBuffer.remaining())

        // Copy "length" bytes from "sourceBuffer" to "buffer".
        // Note: We may need 2 operations if we wrap at the end of the circular buffer
        val copyLength1 = min(copyLength, capacity - endOffset)
        addNBytes(sourceBuffer, copyLength1)

        val copyLength2 = copyLength - copyLength1
        addNBytes(sourceBuffer, copyLength2)

        return copyLength
    }

    /**
     * Reads bytes from this [CircularByteBuffer] into [targetBuffer], returning the number
     * of bytes read.
     *
     * * If there is not enough room in [targetBuffer], only a subset of this [CircularByteBuffer]
     * is copied over.
     * * If [CircularByteBuffer] is empty, this method returns `zero` and no bytes are
     * copied to [targetBuffer].
     */
    fun read(targetBuffer: ByteBuffer): Int {
        val copyLength = min(size, targetBuffer.remaining())

        // Copy "length" bytes from "buffer" to "targetBuffer"
        // Note: We may need 2 operations if we wrap at the end of the circular buffer
        val copyLength1 = min(copyLength, capacity - startOffset)
        readNBytes(targetBuffer, copyLength1)

        val copyLength2 = copyLength - copyLength1
        readNBytes(targetBuffer, copyLength2)

        return copyLength
    }

    private fun addNBytes(sourceBuffer: ByteBuffer, count: Int) {
        assert(count >= 0)

        if (count > 0) {
            sourceBuffer.get(buffer, endOffset, count)
            endOffset = incOffset(endOffset, count)
            size += count
        }
    }

    private fun readNBytes(targetBuffer: ByteBuffer, count: Int) {
        assert(count >= 0)

        if (count > 0) {
            targetBuffer.put(buffer, startOffset, count)
            startOffset = incOffset(startOffset, count)
            size -= count
        }
    }

    private fun incOffset(offset: Int, count: Int): Int {
        assert(offset >= 0)
        assert(offset < capacity)
        assert(count >= 0)
        assert(count <= capacity)

        val result = offset + count
        return if (result >= buffer.size) {
            result - buffer.size
        } else {
            result
        }
    }
}
