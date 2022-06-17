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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer

class AdbChannelExtensionsTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun adbInputChannel_readRemaining_ReadsAllBytes() = runBlockingWithTimeout {
        // Prepare
        val inputBuffer = createTestBuffer(200)
        val inputChannel = ByteBufferAdbInputChannel(inputBuffer)

        // Act
        val resizableBuffer = ResizableBuffer()
        val count = inputChannel.readRemaining(resizableBuffer, 12)

        // Assert
        assertEquals(200, count)
        assertEquals(200, inputBuffer.position())
        assertEquals(200, inputBuffer.limit())
        assertEquals(200, inputBuffer.capacity())
        assertEquals(inputBuffer.limit(), resizableBuffer.position)
        assertBufferEquals(inputBuffer, 0, resizableBuffer, 0, 200)
    }

    @Test
    fun adbInputChannel_readRemaining_AppendsToEndOfBuffer() = runBlockingWithTimeout {
        // Prepare
        val inputLimit = 100
        val inputPosition = 20
        val inputBuffer = createTestBuffer(inputLimit)
        inputBuffer.position(inputPosition)
        val inputChannel = ByteBufferAdbInputChannel(inputBuffer)

        // Act
        val resizableBuffer = ResizableBuffer()
        resizableBuffer.appendBytes(ByteArray(10))
        val count = inputChannel.readRemaining(resizableBuffer, 12)

        // Assert
        assertEquals(inputLimit - inputPosition, count)
        assertEquals(inputLimit, inputBuffer.position())
        assertEquals(inputLimit, inputBuffer.limit())
        assertEquals(inputLimit, inputBuffer.capacity())
        assertEquals(inputBuffer.limit(), resizableBuffer.position - 10 + inputPosition)
        assertBufferEquals(
            inputBuffer,
            inputPosition,
            resizableBuffer,
            10,
            inputLimit - inputPosition
        )
    }

    @Test
    fun adbInputChannel_skipRemaining_SkipsAllBytes() = runBlockingWithTimeout {
        // Prepare
        val inputBuffer = createTestBuffer(200)
        inputBuffer.position(10)
        val inputChannel = ByteBufferAdbInputChannel(inputBuffer)

        // Act
        val count = inputChannel.skipRemaining(bufferSize = 12)

        // Assert
        assertEquals(190, count)
        assertEquals(200, inputBuffer.position())
        assertEquals(200, inputBuffer.limit())
        assertEquals(200, inputBuffer.capacity())
    }

    @Test
    fun adbInputChannel_forwardTo_ForwardsAllBytes() = runBlockingWithTimeout {
        // Prepare
        val inputBuffer = createTestBuffer(200)
        inputBuffer.position(20)
        val inputChannel = ByteBufferAdbInputChannel(inputBuffer)
        val outputBuffer = ResizableBuffer()
        val outputChannel = ByteBufferAdbOutputChannel(outputBuffer)

        // Act
        val count = inputChannel.forwardTo(outputChannel, bufferSize = 15)

        // Assert
        assertEquals(180, count)
        assertEquals(200, inputBuffer.position())
        assertEquals(200, inputBuffer.limit())
        assertEquals(200, inputBuffer.capacity())
        assertEquals(inputBuffer.limit() - 20, outputBuffer.position)
        assertBufferEquals(inputBuffer, 20, outputBuffer, 0, 180)
    }

    /**
     * Creates a [ByteBuffer] where position = 0, limit = [size], capacity =[size]
     */
    private fun createTestBuffer(size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(size)
        buffer.clear()
        for (i in 0 until size) {
            buffer.put((i % 127).toByte())
        }
        buffer.position(0)

        assert(buffer.position() == 0)
        assert(buffer.limit() == size)
        assert(buffer.capacity() == size)
        return buffer
    }

    private fun assertBufferEquals(
        inputBuffer: ByteBuffer,
        inputBufferPosition: Int,
        resizableBuffer: ResizableBuffer,
        resizableBufferPosition: Int,
        count: Int
    ) {
        for (i in 0 until count) {
            assertEquals(
                inputBuffer[inputBufferPosition + i],
                resizableBuffer[resizableBufferPosition + i]
            )
        }
    }
}
