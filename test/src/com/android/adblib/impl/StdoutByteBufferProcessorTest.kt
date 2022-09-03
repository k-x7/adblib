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
package com.android.adblib.impl

import com.android.adblib.impl.StdoutByteBufferProcessor.DirectProcessor
import com.android.adblib.impl.StdoutByteBufferProcessor.StripCrLfProcessor
import org.junit.Assert
import org.junit.Test
import java.nio.ByteBuffer

private const val CR = '\r'
private const val LF = '\n'

class StdoutByteBufferProcessorTest {
    @Test
    fun directProcessorDoesNothing() {
        // Prepare
        val processor = DirectProcessor()
        val buffer = ByteBuffer.allocate(10)

        // Act
        buffer.addChars(listOf('a', 'b', 'c', CR, LF, 'd'))
        buffer.flip()
        val buffer1 = processor.convertBuffer(buffer).clone()
        val buffer2 = processor.convertBufferEnd()?.clone()

        // Assert
        Assert.assertEquals(6, buffer1.remaining())
        buffer1.assertChars(listOf('a', 'b', 'c', CR, LF, 'd'))
        Assert.assertNull(buffer2)
    }

    @Test
    fun stripCrLfProcessorRemovesExtraCrs() {
        // Prepare
        val processor = StripCrLfProcessor()
        val buffer = ByteBuffer.allocate(10)

        // Act
        buffer.addChars(listOf('a', 'b', 'c', CR, LF, 'd'))
        buffer.flip()
        val buffer1 = processor.convertBuffer(buffer).clone()
        val buffer2 = processor.convertBufferEnd()?.clone()

        // Assert
        Assert.assertEquals(5, buffer1.remaining())
        buffer1.assertChars(listOf('a', 'b', 'c', LF, 'd'))
        Assert.assertNull(buffer2)
    }

    @Test
    fun stripCrLfProcessorDoesNotLoseLastCr() {
        // Prepare
        val processor = StripCrLfProcessor()
        val buffer = ByteBuffer.allocate(6)

        // Act
        buffer.addChars(listOf('a', 'b', 'c', CR, LF, CR))
        buffer.flip()
        val buffer1 = processor.convertBuffer(buffer).clone()
        val buffer2 = processor.convertBufferEnd()?.clone()

        // Assert
        Assert.assertEquals(4, buffer1.remaining())
        buffer1.assertChars(listOf('a', 'b', 'c', LF))
        Assert.assertNotNull(buffer2)
        buffer2!!.assertChars(listOf(CR))
    }

    @Test
    fun stripCrLfProcessorDoesNotLoseLastCrOfIntermediateBuffer() {
        // Prepare
        val processor = StripCrLfProcessor()
        val buffer = ByteBuffer.allocate(6)

        // Act
        buffer.addChars(listOf('a', 'b', 'c', CR, LF, CR))
        buffer.flip()
        val buffer1 = processor.convertBuffer(buffer).clone()

        buffer.clear()
        buffer.addChars(listOf(LF, 'e', 'f'))
        buffer.flip()
        val buffer2 = processor.convertBuffer(buffer).clone()

        val buffer3 = processor.convertBufferEnd()?.clone()

        // Assert
        Assert.assertEquals(4, buffer1.remaining())
        buffer1.assertChars(listOf('a', 'b', 'c', LF))

        Assert.assertEquals(3, buffer2.remaining())
        buffer2.assertChars(listOf(LF, 'e', 'f'))

        Assert.assertNull(buffer3)
    }

    @Test
    fun stripCrLfProcessorDoesNotLoseLastCrOfIntermediateBuffer2() {
        // Prepare
        val processor = StripCrLfProcessor()
        val buffer = ByteBuffer.allocate(6)

        // Act
        buffer.addChars(listOf('a', 'b', 'c', CR, LF, CR))
        buffer.flip()
        val buffer1 = processor.convertBuffer(buffer).clone()

        buffer.clear()
        buffer.addChars(listOf('d', 'e', 'f'))
        buffer.flip()
        val buffer2 = processor.convertBuffer(buffer).clone()

        val buffer3 = processor.convertBufferEnd()?.clone()

        // Assert
        Assert.assertEquals(4, buffer1.remaining())
        buffer1.assertChars(listOf('a', 'b', 'c', LF))

        Assert.assertEquals(4, buffer2.remaining())
        buffer2.assertChars(listOf(CR, 'd', 'e', 'f'))

        Assert.assertNull(buffer3)
    }

    @Test
    fun stripCrLfProcessorCanHandleExtraCharacter() {
        // Prepare
        val processor = StripCrLfProcessor()
        val buffer = ByteBuffer.allocate(6)

        // Act
        buffer.addChars(listOf('a', 'b', 'c', CR, LF, CR))
        buffer.flip()
        val buffer1 = processor.convertBuffer(buffer).clone()

        buffer.clear()
        buffer.addChars(listOf('d', 'e', 'f', 'g', 'h', 'i'))
        buffer.flip()
        val buffer2 = processor.convertBuffer(buffer).clone()

        val buffer3 = processor.convertBufferEnd()?.clone()

        // Assert
        Assert.assertEquals(4, buffer1.remaining())
        buffer1.assertChars(listOf('a', 'b', 'c', LF))

        Assert.assertEquals(6, buffer2.remaining())
        buffer2.assertChars(listOf(CR, 'd', 'e', 'f', 'g', 'h'))

        Assert.assertNotNull(buffer3)
        buffer3!!.assertChars(listOf('i'))
    }

    private fun ByteBuffer.addChars(list: List<Char>) {
        list.forEach {
            put(it.code.toByte())
        }
    }

    private fun ByteBuffer.assertChars(list: List<Char>) {
        list.forEachIndexed { index, ch ->
            Assert.assertEquals(ch.code.toByte(), get(index))
        }
    }

    private fun ByteBuffer.clone(): ByteBuffer {
        val savedPosition = this.position()
        val savedLimit = this.limit()
        this.position(0)
        this.limit(this.capacity())

        val result = ByteBuffer.allocate(capacity())
        result.put(this)
        result.limit(savedLimit)
        result.position(savedPosition)

        this.limit(savedLimit)
        this.position(savedPosition)
        return result
    }
}
