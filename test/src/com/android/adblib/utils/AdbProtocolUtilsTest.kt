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
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import java.nio.file.attribute.FileTime

class AdbProtocolUtilsTest {
    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun bufferToByteDumpStringShouldWorkForSmallStrings() {
        // Prepare
        val buffer = createBuffer("123456789")
        val positionBefore = buffer.position()
        val limitBefore = buffer.limit()

        // Act
        val result = AdbProtocolUtils.bufferToByteDumpString(buffer)

        // Assert
        assertEquals("313233343536373839 123456789", result)
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
        assertEquals("3132333435363738396162636465666768696a6b6c6d6e6f 123456789abcdefghijklmno [truncated]", result)
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
        assertEquals("3132 12", result)
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

    @Test
    fun encodePrefixLengthWorksForSmallValue() {
        // Prepare
        val length = 10

        // Act
        val result = AdbProtocolUtils.encodeLengthPrefix(length)

        // Assert
        assertEquals(protocolLengthValue("000A"), result)
    }

    @Test
    fun encodePrefixLengthWorksForLargeValue() {
        // Prepare
        val length = 30000

        // Act
        val result = AdbProtocolUtils.encodeLengthPrefix(length)

        // Assert
        assertEquals(protocolLengthValue("7530"), result)
    }

    @Test
    fun encodePrefixLengthWorksForMinValue() {
        // Prepare
        val length = 0

        // Act
        val result = AdbProtocolUtils.encodeLengthPrefix(length)

        // Assert
        assertEquals(protocolLengthValue("0000"), result)
    }

    @Test
    fun encodePrefixLengthWorksForMaxValue() {
        // Prepare
        val length = 65_535

        // Act
        val result = AdbProtocolUtils.encodeLengthPrefix(length)

        // Assert
        assertEquals(protocolLengthValue("FFFF"), result)
    }

    @Test
    fun encodePrefixLengthThrowsForInvalidSmallValue() {
        // Prepare
        val length = -1

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        val result = AdbProtocolUtils.encodeLengthPrefix(length)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun encodePrefixLengthThrowsForInvalidLargeValue() {
        // Prepare
        val length = 100_000

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        val result = AdbProtocolUtils.encodeLengthPrefix(length)

        // Assert
        fail("Should not reach")
    }

    private fun protocolLengthValue(value: String): Int {
        return (value[0].code shl 24) +
               (value[1].code shl 16) +
               (value[2].code shl 8) +
               (value[3].code)
    }

    private fun createBuffer(value: String): ByteBuffer {
        val bytes = value.toByteArray(AdbProtocolUtils.ADB_CHARSET)
        return ByteBuffer.wrap(bytes, 0, bytes.size)
    }
}
