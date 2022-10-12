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

import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import kotlin.test.assertEquals

class CircularByteBufferTest {
    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun addSingleByteWorks() {
        // Prepare
        val buffer = CircularByteBuffer(10)

        // Act
        val result = buffer.add(createSourceBuffer(0xff))

        // Assert
        assertEquals(1, result)
        assertEquals(1, buffer.size)
        assertEquals(9, buffer.remaining)
        assertEquals(10, buffer.capacity)
    }

    @Test
    fun addBytesWorks() {
        // Prepare
        val buffer = CircularByteBuffer(10)

        // Act
        val result = buffer.add(createSourceBuffer(0x10, 0x20, 0x05, 0x80))

        // Assert
        assertEquals(4, result)
        assertEquals(4, buffer.size)
        assertEquals(6, buffer.remaining)
        assertEquals(10, buffer.capacity)
    }

    @Test
    fun addTooManyBytesWorks() {
        // Prepare
        val buffer = CircularByteBuffer(10)
        buffer.add(ByteBuffer.allocate(5))

        // Act
        val result = buffer.add(ByteBuffer.allocate(20))

        // Assert
        assertEquals(5, result)
        assertEquals(10, buffer.size)
        assertEquals(0, buffer.remaining)
        assertEquals(10, buffer.capacity)
    }

    @Test
    fun clearWorks() {
        // Prepare
        val buffer = CircularByteBuffer(10)
        buffer.add(createSourceBuffer(0xff))

        // Act
        buffer.clear()

        // Assert
        assertEquals(0, buffer.size)
        assertEquals(10, buffer.remaining)
        assertEquals(10, buffer.capacity)
    }

    @Test
    fun readSingleByteWorks() {
        // Prepare
        val buffer = CircularByteBuffer(10)
        buffer.add(createSourceBuffer(0xff))

        // Act
        val dstBuffer = ByteBuffer.allocate(1)
        val result = buffer.read(dstBuffer)
        dstBuffer.flip()

        // Assert
        assertEquals(1, result)
        assertEquals(1, dstBuffer.remaining())
        assertEquals(listOf(0xff), dstBuffer.toIntList())
        assertEquals(0, buffer.size)
        assertEquals(10, buffer.remaining)
        assertEquals(10, buffer.capacity)
    }

    @Test
    fun readTooManyBytesWorks() {
        // Prepare
        val buffer = CircularByteBuffer(10)
        buffer.add(createSourceBuffer(0xff, 0x12, 0x25))

        // Act
        val dstBuffer = ByteBuffer.allocate(10)
        val result = buffer.read(dstBuffer)
        dstBuffer.flip()

        // Assert
        assertEquals(3, result)
        assertEquals(3, dstBuffer.remaining())
        assertEquals(listOf(0xff, 0x12, 0x25), dstBuffer.toIntList())
        assertEquals(0, buffer.size)
        assertEquals(10, buffer.remaining)
        assertEquals(10, buffer.capacity)
    }

    private fun createSourceBuffer(vararg bytes: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(bytes.size)
        bytes.forEach {
            buffer.put(it.toByte())
        }
        buffer.flip()
        return buffer
    }

    private fun ByteBuffer.toIntList(): List<Int> {
        val result = mutableListOf<Int>()
        repeat(remaining()) {
            result.add(this.get().toUByte().toInt())
        }
        this.position(this.position() - result.size)
        return result
    }
}
