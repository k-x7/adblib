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

import com.android.adblib.ShellCollector
import com.android.adblib.TextShellCollector
import com.android.adblib.testingutils.ByteBufferUtils
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class TextShellCollectorTest {

    @Test
    fun testNoOutputIsEmptyText() {
        // Prepare
        val shellCollector = TextShellCollector()
        val flowCollector = TextFlowCollector()

        // Act
        collectStrings(shellCollector, flowCollector)

        // Assert
        Assert.assertEquals("", flowCollector.text)
    }

    @Test
    fun testEmptyStringIsEmptyText() {
        // Prepare
        val shellCollector = TextShellCollector()
        val flowCollector = TextFlowCollector()

        // Act
        collectStrings(shellCollector, flowCollector, "")

        // Assert
        Assert.assertEquals("", flowCollector.text)
    }

    @Test
    fun testSingleNewLineIsPreserver() {
        // Prepare
        val shellCollector = TextShellCollector()
        val flowCollector = TextFlowCollector()

        // Act
        collectStrings(shellCollector, flowCollector, "\n")

        // Assert
        Assert.assertEquals("\n", flowCollector.text)
    }

    @Test
    fun testSingleCharacterIsPreserved() {
        // Prepare
        val shellCollector = TextShellCollector()
        val flowCollector = TextFlowCollector()

        // Act
        collectStrings(shellCollector, flowCollector, "x")

        // Assert
        Assert.assertEquals("x", flowCollector.text)
    }

    @Test
    fun testTrailingNewLineIsPreserved() {
        // Prepare
        val shellCollector = TextShellCollector()
        val flowCollector = TextFlowCollector()

        // Act
        collectStrings(shellCollector, flowCollector, "x\n")

        // Assert
        Assert.assertEquals("x\n", flowCollector.text)
    }

    @Test
    fun testOverlappingChunksAreMerged() {
        // Prepare
        val linesCollector = TextShellCollector(10)
        val flowCollector = TextFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector, "12345678901234", "56\nab\ncdefg")

        // Assert
        Assert.assertEquals("1234567890123456\nab\ncdefg", flowCollector.text)
    }

    private fun collectStrings(
        shellCollector: ShellCollector<String>,
        flowCollector: FlowCollector<String>,
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

    private class TextFlowCollector : FlowCollector<String> {

        var text = ""

        override suspend fun emit(value: String) {
            this.text += value
        }
    }
}
