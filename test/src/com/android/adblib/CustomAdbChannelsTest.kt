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
package com.android.adblib

import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException

class CustomAdbChannelsTest {
    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testEmptyInputChannelHasNoData() {
        // Prepare
        val inputChannel = EmptyAdbInputChannel()
        val buffer = ByteBuffer.allocate(100)

        // Act
        val count = runBlocking {
            inputChannel.read(buffer)
        }

        // Assert
        Assert.assertEquals(-1, count)
    }

    @Test
    fun testByteBufferInputChannelReadsAllDataFromBuffer() {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        buffer.limit(65)
        val inputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val counts = mutableListOf<Int>()
        runBlocking {
            val localBuffer = ByteBuffer.allocate(10)
            while (true) {
                localBuffer.clear()
                val count = inputChannel.read(localBuffer)
                if (count == -1) {
                    break
                }
                counts.add(count)
            }
        }

        // Assert
        Assert.assertEquals(7, counts.size)
        Assert.assertEquals(10, counts[0])
        Assert.assertEquals(10, counts[1])
        Assert.assertEquals(10, counts[2])
        Assert.assertEquals(10, counts[3])
        Assert.assertEquals(10, counts[4])
        Assert.assertEquals(10, counts[5])
        Assert.assertEquals(5, counts[6])
    }

    @Test
    fun testByteBufferInputChannelReadsDataFromCurrentPosition() {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        buffer.position(10)
        buffer.limit(65)
        val inputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val counts = mutableListOf<Int>()
        runBlocking {
            val localBuffer = ByteBuffer.allocate(10)
            while (true) {
                localBuffer.clear()
                val count = inputChannel.read(localBuffer)
                if (count == -1) {
                    break
                }
                counts.add(count)
            }
        }

        // Assert
        Assert.assertEquals(6, counts.size)
        Assert.assertEquals(10, counts[0])
        Assert.assertEquals(10, counts[1])
        Assert.assertEquals(10, counts[2])
        Assert.assertEquals(10, counts[3])
        Assert.assertEquals(10, counts[4])
        Assert.assertEquals(5, counts[5])
    }

    @Test
    fun testByteBufferInputChannelThrowsAfterClose() {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        val inputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        exceptionRule.expect(ClosedChannelException::class.java)
        runBlocking {
            inputChannel.close()

            val localBuffer = ByteBuffer.allocate(10)
            inputChannel.read(localBuffer)
        }

        // Assert
        Assert.fail("Test should have thrown an exception")
    }

    @Test
    fun testByteBufferOutputChannelGrowsBufferIfNeeded() {
        // Prepare
        val buffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(buffer)
        val iterations = 10
        val bufferSize = 150

        // Act
        val counts = mutableListOf<Int>()
        runBlocking {
            val localBuffer = ByteBuffer.allocate(bufferSize)
            for (i in 0 until iterations) {
                localBuffer.clear()
                val count = outputChannel.write(localBuffer)
                counts.add(count)
            }
        }

        // Assert
        Assert.assertEquals(iterations, counts.size)
        repeat(counts.size) {
            Assert.assertEquals(bufferSize, counts[0])
        }

        Assert.assertEquals(1_500, buffer.position)
        Assert.assertTrue(buffer.capacity > 1_500)
        val byteBuffer = buffer.forChannelWrite()
        Assert.assertEquals(0, byteBuffer.position())
        Assert.assertEquals(1_500, byteBuffer.limit())
    }

    @Test
    fun testByteBufferOutputChannelAppendsToExistingBuffer() {
        // Prepare
        val buffer = ResizableBuffer()
        buffer.appendByte(5)
        val outputChannel = ByteBufferAdbOutputChannel(buffer)
        val iterations = 10
        val bufferSize = 150

        // Act
        val counts = mutableListOf<Int>()
        runBlocking {
            val localBuffer = ByteBuffer.allocate(bufferSize)
            for (i in 0 until iterations) {
                localBuffer.clear()
                val count = outputChannel.write(localBuffer)
                counts.add(count)
            }
        }

        // Assert
        Assert.assertEquals(iterations, counts.size)
        repeat(counts.size) {
            Assert.assertEquals(bufferSize, counts[0])
        }

        Assert.assertEquals(1_501, buffer.position)
        Assert.assertTrue(buffer.capacity > 1_501)
        val byteBuffer = buffer.forChannelWrite()
        Assert.assertEquals(0, byteBuffer.position())
        Assert.assertEquals(1_501, byteBuffer.limit())
        Assert.assertEquals(5.toByte() , byteBuffer[0])
    }

    @Test
    fun testByteBufferOutputChannelThrowsAfterClose() {
        // Prepare
        val buffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(buffer)

        // Act
        exceptionRule.expect(ClosedChannelException::class.java)
        runBlocking {
            outputChannel.close()

            val localBuffer = ByteBuffer.allocate(10)
            outputChannel.write(localBuffer)
        }

        // Assert
        Assert.fail("Test should have thrown an exception")
    }

    @Test
    fun testInputChannelSliceThrowsAfterClose() {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        buffer.put(ByteArray(70))
        val bufferInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        exceptionRule.expect(ClosedChannelException::class.java)
        runBlocking {
            val inputChannel = AdbInputChannelSlice(bufferInputChannel, 50)
            inputChannel.close()

            val localBuffer = ByteBuffer.allocate(10)
            inputChannel.read(localBuffer)
        }

        // Assert
        Assert.fail("Test should have thrown an exception")
    }

    @Test
    fun testInputChannelSliceReadsAllData() = runBlockingWithTimeout {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        buffer.put(ByteArray(70))
        for(i in 0 until buffer.capacity()) {
            buffer.put(i, i.toByte())
        }
        buffer.flip()
        val bufferInputChannel = ByteBufferAdbInputChannel(buffer)
        val channelSlice = AdbInputChannelSlice(bufferInputChannel, 50)

        // Act
        val readBuffer = ByteBuffer.allocate(20)
        readBuffer.clear()
        val count1 = channelSlice.read(readBuffer)
        val pos1 = readBuffer.position()
        val array1 = (0 until readBuffer.position()).map { readBuffer.get(it) }

        readBuffer.clear()
        val count2 = channelSlice.read(readBuffer)
        val pos2 = readBuffer.position()
        val array2 = (0 until readBuffer.position()).map { readBuffer.get(it) }

        readBuffer.clear()
        val count3 = channelSlice.read(readBuffer)
        val pos3 = readBuffer.position()
        val array3 = (0 until readBuffer.position()).map { readBuffer.get(it) }

        // Assert
        Assert.assertEquals(20, count1)
        Assert.assertEquals(20, pos1)
        Assert.assertEquals((0 until 20).map { it.toByte() }, array1)

        Assert.assertEquals(20, count2)
        Assert.assertEquals(20, pos2)
        Assert.assertEquals((20 until 40).map { it.toByte() }, array2)

        Assert.assertEquals(10, count3)
        Assert.assertEquals(10, pos3)
        Assert.assertEquals((40 until 50).map { it.toByte() }, array3)
    }

    @Test
    fun testInputChannelSliceStopAfterLimit() {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        buffer.put(ByteArray(70))
        buffer.flip()
        val bufferInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val count = runBlocking {
            val inputChannel = AdbInputChannelSlice(bufferInputChannel, 50)

            val localBuffer = ByteBuffer.allocate(80)
            inputChannel.read(localBuffer)
        }

        // Assert
        Assert.assertEquals(50, count)
    }

    @Test
    fun testInputChannelSliceStopAfterLimitWithSmallReads() {
        // Prepare
        val buffer = ByteBuffer.allocate(100)
        buffer.put(ByteArray(70))
        buffer.flip()
        val bufferInputChannel = ByteBufferAdbInputChannel(buffer)

        // Act
        val count = runBlocking {
            val inputChannel = AdbInputChannelSlice(bufferInputChannel, 50)

            var returnedCount = 0
            while(true) {
                // Read 3 bytes at a time
                val localBuffer = ByteBuffer.allocate(7)
                localBuffer.position(4)
                val readCount = inputChannel.read(localBuffer)
                if (readCount < 0) {
                    break
                }
                returnedCount += readCount
            }
            returnedCount
        }

        // Assert
        Assert.assertEquals(50, count)
    }
}
