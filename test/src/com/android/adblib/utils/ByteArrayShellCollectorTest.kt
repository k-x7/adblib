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

import com.android.adblib.ShellCollector
import com.android.adblib.testingutils.ByteBufferUtils
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

/**
 * Tests for [ByteArrayShellCollector]
 */
class ByteArrayShellCollectorTest {

    @Test
    fun testNoOutputIsEmptyBytes() {
        // Prepare
        val bytesCollector = ByteArrayShellCollector()
        val flowCollector = BytesFlowCollector()

        // Act
        collect(bytesCollector, flowCollector)

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.data)
    }

    @Test
    fun testEmptyBytesIsEmptyBytes() {
        // Prepare
        val shellCollector = ByteArrayShellCollector()
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "")

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.data)
    }

    @Test
    fun testCrLfWithoutRemoval() {
        // Prepare
        val shellCollector = ByteArrayShellCollector(removeCarriageReturns = false)
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "abc\r\n")

        // Assert
        Assert.assertEquals(listOf("abc\r\n"), flowCollector.data)
    }

    @Test
    fun testCrLfRemoval() {
        // Prepare
        val shellCollector = ByteArrayShellCollector(removeCarriageReturns = true)
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "abc\r\n123\r\n", "\r\n", )

        // Assert
        Assert.assertEquals(listOf("abc\n123\n\n"), flowCollector.data)
    }

    @Test
    fun testCrLfRemovalInDifferentBuffers() {
        // Prepare
        val shellCollector = ByteArrayShellCollector(removeCarriageReturns = true)
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "abc\r", "\n", )

        // Assert
        Assert.assertEquals(listOf("abc\n"), flowCollector.data)
    }

    @Test
    fun testCrLfRemovalLastByteIsCr() {
        // Prepare
        val shellCollector = ByteArrayShellCollector(removeCarriageReturns = true)
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "abc\r", )

        // Assert
        Assert.assertEquals(listOf("abc\r"), flowCollector.data)
    }

    @Test
    fun testCrLfRemovalCrWithoutLf() {
        // Prepare
        val shellCollector = ByteArrayShellCollector(removeCarriageReturns = true)
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "abc\r123", )

        // Assert
        Assert.assertEquals(listOf("abc\r123"), flowCollector.data)
    }

    @Test
    fun testCrLfRemovalCrWithoutLfInDifferentBuffers() {
        // Prepare
        val shellCollector = ByteArrayShellCollector(removeCarriageReturns = true)
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "abc\r", "123", )

        // Assert
        Assert.assertEquals(listOf("abc\r123"), flowCollector.data)
    }

    private fun collect(
        shellCollector: ByteArrayShellCollector,
        flowCollector: BytesFlowCollector,
        vararg values: String
    ) {
        runBlocking {
            shellCollector.start(flowCollector)
            values.forEach { value ->
                shellCollector.collect(flowCollector, ByteBufferUtils.stringToByteBuffer(value))
            }
            shellCollector.end(flowCollector)
        }
    }

    private class BytesFlowCollector : FlowCollector<ByteArray> {

        var data = mutableListOf<String>()

        override suspend fun emit(value: ByteArray) {
            // ByteArray does not have equals() and it's also easier to read the test if we convert
            // them to strings.
            data.add(String(value))
        }
    }

}
