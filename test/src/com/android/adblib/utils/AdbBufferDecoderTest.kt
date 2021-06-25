/*
 * Copyright (C) 2021 The Android Open Source Project
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

import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer

class AdbBufferDecoderTest {

    var currentString: String? = null

    @Test
    fun testDecoderIsEmpty() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        decoder.decodeBuffer(createBuffer(""))

        // Assert
        Assert.assertNull(currentString)
    }

    @Test
    fun testDecoderDecodesSingleCharacter() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        decoder.decodeBuffer(createBuffer("a"))

        // Assert
        Assert.assertEquals("a", currentString)
    }

    @Test
    fun testDecoderDecodesSingleMultiByteCharacter() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        decoder.decodeBuffer(createBuffer("⿉"))

        // Assert
        Assert.assertEquals("⿉", currentString)
    }

    @Test
    fun testDecoderSupportsOverlappedMultiByteCharacter() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        val buffer = createBuffer("⿉" /*E2BF89*/)
        buffer.limit(1)
        decoder.decodeBuffer(buffer)
        buffer.limit(2)
        decoder.decodeBuffer(buffer)
        buffer.limit(3)
        decoder.decodeBuffer(buffer)

        // Assert
        Assert.assertEquals("⿉", currentString)
    }

    @Test
    fun testDecoderConvertsUnmappableCharacterToQuestionMark() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        val buffer = createBuffer(0xff, 'a'.toInt(), 0xfe)
        decoder.decodeBuffer(buffer)

        // Assert
        Assert.assertEquals("?a?", currentString)
    }

    @Test
    fun testDecoderAccumulatesSingleCharacters() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        decoder.decodeBuffer(createBuffer("a"))
        decoder.decodeBuffer(createBuffer("b"))
        decoder.decodeBuffer(createBuffer("c"))

        // Assert
        Assert.assertEquals("abc", currentString)
    }

    @Test
    fun testDecoderAllowsStringLargerThanInternalBuffer() {
        // Prepare
        val decoder = AdbBufferDecoder(10)

        // Act
        val input = "1234567890123456789012345"
        val buffer = createBuffer(input)
        decoder.decodeBuffer(buffer)

        // Assert
        Assert.assertEquals(input, currentString)
    }

    private fun createBuffer(str: String): ByteBuffer {
        return AdbProtocolUtils.ADB_CHARSET.encode(str)
    }

    private fun createBuffer(vararg bytes: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(bytes.size)
        bytes.forEach { ch ->
            buffer.put(ch.toByte())
        }
        buffer.flip()
        return buffer
    }

    private fun AdbBufferDecoder.decodeBuffer(buffer: ByteBuffer) {
        decodeBuffer(buffer) { charBuffer ->
            if (charBuffer.isNotEmpty()) {
                currentString = (currentString ?: "") + charBuffer
            }
        }
    }
}
