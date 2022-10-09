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

import com.android.adblib.LineShellCollector
import com.android.adblib.testingutils.ByteBufferUtils
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class LineShellCollectorTest {

    @Test
    fun testNoOutputIsOneLine() {
        // Prepare
        val linesCollector = LineShellCollector()
        val flowCollector = LinesFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector)

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.lines)
    }

    @Test
    fun testEmptyStringIsOneLine() {
        // Prepare
        val linesCollector = LineShellCollector()
        val flowCollector = LinesFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector, "")

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.lines)
    }

    @Test
    fun testSingleNewLineIsTwoLine() {
        // Prepare
        val linesCollector = LineShellCollector()
        val flowCollector = LinesFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector, "\n")

        // Assert
        Assert.assertEquals(listOf("", ""), flowCollector.lines)
    }

    @Test
    fun testSingleCharacterIsOneLine() {
        // Prepare
        val linesCollector = LineShellCollector()
        val flowCollector = LinesFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector, "x")

        // Assert
        Assert.assertEquals(listOf("x"), flowCollector.lines)
    }

    @Test
    fun testTrailingNewLineIsTwoLines() {
        // Prepare
        val linesCollector = LineShellCollector()
        val flowCollector = LinesFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector, "x\n")

        // Assert
        Assert.assertEquals(listOf("x", ""), flowCollector.lines)
    }

    @Test
    fun testOverlappingChunksAreMerged() {
        // Prepare
        val linesCollector = LineShellCollector(10)
        val flowCollector = LinesFlowCollector()

        // Act
        collectStrings(linesCollector, flowCollector, "12345678901234", "56\nab\ncdefg")

        // Assert
        Assert.assertEquals(listOf("1234567890123456", "ab", "cdefg"), flowCollector.lines)
    }

    private fun collectStrings(
      linesCollector: LineShellCollector,
      flowCollector: FlowCollector<String>,
      vararg values: String
    ) {
        runBlocking {
            linesCollector.start(flowCollector)
            values.forEach { value ->
                linesCollector.collect(flowCollector, ByteBufferUtils.stringToByteBuffer(value))
            }
            linesCollector.end(flowCollector)
        }
    }

    private class LinesFlowCollector : FlowCollector<String> {

        val lines = ArrayList<String>()

        override suspend fun emit(value: String) {
            lines.add(value)
        }
    }
}
