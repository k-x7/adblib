package com.android.adblib.utils

import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer

class ResizableBufferTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testGrowsAsNeeded() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)

        // Act
        buffer.appendString("foo", Charsets.UTF_8)
        buffer.appendString("bar", Charsets.UTF_8)
        buffer.appendString("blah", Charsets.UTF_8)
        val data = buffer.forChannelWrite()

        // Assert
        Assert.assertEquals(10, data.remaining())
        Assert.assertEquals('f'.toByte(), data.get())
        Assert.assertEquals('o'.toByte(), data.get())
        Assert.assertEquals('o'.toByte(), data.get())
        Assert.assertEquals('b'.toByte(), data.get())
        Assert.assertEquals('a'.toByte(), data.get())
        Assert.assertEquals('r'.toByte(), data.get())
        Assert.assertEquals('b'.toByte(), data.get())
        Assert.assertEquals('l'.toByte(), data.get())
        Assert.assertEquals('a'.toByte(), data.get())
        Assert.assertEquals('h'.toByte(), data.get())
    }

    @Test
    fun testAppendMethodsWork() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)

        // Act
        buffer.appendString("foo", Charsets.UTF_8)
        buffer.appendBytes(byteArrayOf(1, 2, 3))
        buffer.appendBytes(ByteBuffer.allocate(3).apply {
            put(byteArrayOf(4, 5, 6))
            flip()
        })
        val data = buffer.forChannelWrite()

        // Assert
        Assert.assertEquals(9, data.remaining())
        Assert.assertEquals('f'.toByte(), data.get())
        Assert.assertEquals('o'.toByte(), data.get())
        Assert.assertEquals('o'.toByte(), data.get())
        Assert.assertEquals(1.toByte(), data.get())
        Assert.assertEquals(2.toByte(), data.get())
        Assert.assertEquals(3.toByte(), data.get())
        Assert.assertEquals(4.toByte(), data.get())
        Assert.assertEquals(5.toByte(), data.get())
        Assert.assertEquals(6.toByte(), data.get())
    }

    @Test
    fun testClearWorks() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)
        buffer.appendString("foo", Charsets.UTF_8)

        // Act
        buffer.clear()

        // Assert
        val data = buffer.forChannelWrite()
        Assert.assertEquals(0, data.position())
        Assert.assertEquals(0, data.limit())
        Assert.assertEquals(0, data.remaining())
    }

    @Test
    fun testClearToPositionWorks() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)
        buffer.appendString("foo", Charsets.UTF_8)

        // Act
        buffer.clearToPosition(2)

        // Assert
        val data = buffer.forChannelRead(5)
        Assert.assertEquals(2, data.position())
        Assert.assertEquals(7, data.limit())
        Assert.assertEquals(5, data.remaining())
    }

    @Test
    fun testForWritingWorks() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)

        // Act
        buffer.clear()
        buffer.appendString("foo", Charsets.UTF_8)
        buffer.appendString("foo", Charsets.UTF_8)
        val data = buffer.forChannelWrite()

        // Assert
        Assert.assertEquals(0, data.position())
        Assert.assertEquals(6, data.limit())
    }

    @Test
    fun testForWritingThrowsIfCalledAfterForReading() {
        // Prepare
        val buffer = ResizableBuffer()
        buffer.forChannelRead(5)

        // Act
        exceptionRule.expect(java.lang.IllegalStateException::class.java)
        buffer.forChannelWrite()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testForReadingWorks() {
        // Prepare
        val buffer = ResizableBuffer(10, 10)

        // Act
        buffer.clear()
        val data = buffer.forChannelRead(10)

        // Assert
        Assert.assertEquals(0, data.position())
        Assert.assertEquals(10, data.limit())
    }

    @Test
    fun testForReadingThrowsForInvalidLength() {
        // Prepare
        val buffer = ResizableBuffer(10, 10)

        // Act
        exceptionRule.expect(java.lang.IllegalArgumentException::class.java)
        /*val data = */buffer.forChannelRead(-10)

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testForReadingThrowsIfCalledAfterForWriting() {
        // Prepare
        val buffer = ResizableBuffer(10, 10)
        buffer.appendBytes(byteArrayOf(1, 2, 3))
        buffer.forChannelWrite()

        // Act
        exceptionRule.expect(java.lang.IllegalStateException::class.java)
        buffer.forChannelRead(10)

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testAfterReadingWorks() {
        // Prepare
        val buffer = ResizableBuffer(10, 10)
        buffer.forChannelRead(10).apply {
            put(1)
            put(2)
            put(3)
            put(4)
        }

        // Act
        val data = buffer.afterChannelRead()

        // Assert
        Assert.assertEquals(0, data.position())
        Assert.assertEquals(4, data.limit())
        Assert.assertEquals(1.toByte(), data.get())
        Assert.assertEquals(2.toByte(), data.get())
        Assert.assertEquals(3.toByte(), data.get())
        Assert.assertEquals(4.toByte(), data.get())
    }

    @Test
    fun testAfterReadingThrowsIfCalledBeforeForReading() {
        // Prepare
        val buffer = ResizableBuffer(10, 10)

        // Act
        exceptionRule.expect(IllegalStateException::class.java)
        /*val data = */buffer.afterChannelRead()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testAfterReadingThrowsIfCalledTwice() {
        // Prepare
        val buffer = ResizableBuffer(10, 10)
        buffer.forChannelRead(10)

        // Act
        /*val data = */buffer.afterChannelRead()
        exceptionRule.expect(IllegalStateException::class.java)
        /*val data = */buffer.afterChannelRead()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testBufferCanReachMaxCapacity() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)

        // Act (should throw)
        buffer.appendString("1234567890", Charsets.UTF_8)

        // Assert
        Assert.assertEquals(10, buffer.forChannelWrite().remaining())
    }

    @Test
    fun testBufferCannotExceedMaxCapacity() {
        // Prepare
        val buffer = ResizableBuffer(0, 10)

        // Act (should throw)
        exceptionRule.expect(IllegalArgumentException::class.java)
        buffer.appendString("12345678901", Charsets.UTF_8)

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testGetAtIndexIgnoresPosition() {
        // Prepare
        val buffer = ResizableBuffer(10)

        // Act (should throw)
        exceptionRule.expect(IndexOutOfBoundsException::class.java)
        buffer.get(10)

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testGetAtIndexThrowsIfPastLimit() {
        // Prepare
        val buffer = ResizableBuffer(10)
        buffer.appendByte(5)
        buffer.appendByte(6)
        buffer.appendByte(7)
        buffer.forChannelWrite() // pos = 0, limit = 3

        // Act (should throw)
        exceptionRule.expect(IndexOutOfBoundsException::class.java)
        buffer.get(3)

        // Assert
        Assert.fail("Should not reach")
    }
}
