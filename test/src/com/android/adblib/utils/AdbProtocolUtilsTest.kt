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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.file.attribute.FileTime

class AdbProtocolUtilsTest {

    @Test
    fun bufferToByteDumpStringShouldWorkForSmallStrings() {
        // Prepare
        val buffer = createBuffer("123456789")
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.bufferToByteDumpString(buffer)

        // Assert
        assertEquals("0x313233343536373839 (\"123456789\")", result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun bufferToByteDumpStringShouldTruncateLongStrings() {
        // Prepare
        val buffer = createBuffer("123456789abcdefghijklmnopqrstuvwxyz")
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.bufferToByteDumpString(buffer)

        // Assert
        assertEquals("0x31323334353637383961626364656667 (\"123456789abcdefg\")", result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun bufferToByteDumpStringShouldUseBufferRemainingSpace() {
        // Prepare
        val buffer = createBuffer("0123456789")
        buffer.position(1)
        buffer.limit(3)
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.bufferToByteDumpString(buffer)

        // Assert
        assertEquals("0x3132 (\"12\")", result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun isOkayShouldWork() {
        // Prepare
        val buffer = createBuffer("OKAY")

        // Act
        val result = AdbProtocolUtils.isOkay(buffer)

        // Assert
        assertTrue(result)
    }

    @Test
    fun isOkayShouldLookAtBufferRemainingSpace() {
        // Prepare
        val buffer = createBuffer("-OKAY-")
        buffer.position(1)
        buffer.limit(5)
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.isOkay(buffer)

        // Assert
        assertTrue(result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun isOkayShouldFailForInvalidPosition() {
        // Prepare
        val buffer = createBuffer("OKAY")
        buffer.position(1)
        buffer.limit(4)
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.isOkay(buffer)

        // Assert
        assertFalse(result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun isFailShouldWork() {
        // Prepare
        val buffer = createBuffer("FAIL")

        // Act
        val result = AdbProtocolUtils.isFail(buffer)

        // Assert
        assertTrue(result)
    }

    @Test
    fun isFailShouldLookAtBufferRemainingSpace() {
        // Prepare
        val buffer = createBuffer("-FAIL-")
        buffer.position(1)
        buffer.limit(5)
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.isFail(buffer)

        // Assert
        assertTrue(result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun isFailShouldFailForInvalidPosition() {
        // Prepare
        val buffer = createBuffer("FAIL")
        buffer.position(1)
        buffer.limit(4)
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.isFail(buffer)

        // Assert
        assertFalse(result)
        assertEquals(positionBefore, buffer.position())
        assertEquals(limitBefore, buffer.limit())
    }

    @Test
    fun convertFileTimeToEpochSecondsShouldWork() {
        // Prepare
        val time = FileTime.fromMillis(1_000_000)

        // Act
        val result = AdbProtocolUtils.convertFileTimeToEpochSeconds(time)

        // Assert
        assertEquals(1_000, result)
    }

    @Test
    fun convertFileTimeToEpochSecondsShouldWorkForNegativeValues() {
        // Prepare
        val time = FileTime.fromMillis(-1_000_000)

        // Act
        val result = AdbProtocolUtils.convertFileTimeToEpochSeconds(time)

        // Assert
        assertEquals(-1_000, result)
    }

    private fun createBuffer(value: String): ByteBuffer {
        val bytes = value.toByteArray(AdbProtocolUtils.ADB_CHARSET)
        return ByteBuffer.wrap(bytes, 0, bytes.size)
    }
}
