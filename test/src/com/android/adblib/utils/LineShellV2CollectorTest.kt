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

import com.android.adblib.LineShellV2Collector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.ShellCommandOutputElement.StderrLine
import com.android.adblib.ShellCommandOutputElement.StdoutLine
import com.android.adblib.testingutils.ByteBufferUtils
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class LineShellV2CollectorTest {

    @Test
    fun testNoOutputIsOneLine_stdout() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStdout(linesCollector, flowCollector)

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.getStdouts())
    }

    @Test
    fun testEmptyStringIsOneLine_stdout() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStdout(linesCollector, flowCollector, "")

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.getStdouts())
    }

    @Test
    fun testSingleNewLineIsTwoLine_stdout() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStdout(linesCollector, flowCollector, "\n")

        // Assert
        Assert.assertEquals(listOf("", ""), flowCollector.getStdouts())
    }

    @Test
    fun testSingleCharacterIsOneLine_stdout() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStdout(linesCollector, flowCollector, "x")

        // Assert
        Assert.assertEquals(listOf("x"), flowCollector.getStdouts())
    }

    @Test
    fun testTrailingNewLineIsTwoLines_stdout() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStdout(linesCollector, flowCollector, "x\n")

        // Assert
        Assert.assertEquals(listOf("x", ""), flowCollector.getStdouts())
    }

    @Test
    fun testOverlappingChunksAreMerged_stdout() {
        // Prepare
        val linesCollector = LineShellV2Collector(10)
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStdout(linesCollector, flowCollector, "12345678901234", "56\nab\ncdefg")

        // Assert
        Assert.assertEquals(listOf("1234567890123456", "ab", "cdefg"), flowCollector.getStdouts())
    }

    @Test
    fun testNoOutputIsOneLine_stderr() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStderr(linesCollector, flowCollector)

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.getStderrs())
    }

    @Test
    fun testEmptyStringIsOneLine_stderr() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStderr(linesCollector, flowCollector, "")

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.getStderrs())
    }

    @Test
    fun testSingleNewLineIsTwoLine_stderr() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStderr(linesCollector, flowCollector, "\n")

        // Assert
        Assert.assertEquals(listOf("", ""), flowCollector.getStderrs())
    }

    @Test
    fun testSingleCharacterIsOneLine_stderr() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStderr(linesCollector, flowCollector, "x")

        // Assert
        Assert.assertEquals(listOf("x"), flowCollector.getStderrs())
    }

    @Test
    fun testTrailingNewLineIsTwoLines_stderr() {
        // Prepare
        val linesCollector = LineShellV2Collector()
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStderr(linesCollector, flowCollector, "x\n")

        // Assert
        Assert.assertEquals(listOf("x", ""), flowCollector.getStderrs())
    }

    @Test
    fun testOverlappingChunksAreMerged_stderr() {
        // Prepare
        val linesCollector = LineShellV2Collector(10)
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStderr(linesCollector, flowCollector, "12345678901234", "56\nab\ncdefg")

        // Assert
        Assert.assertEquals(listOf("1234567890123456", "ab", "cdefg"), flowCollector.getStderrs())
    }

    @Test
    fun testOverlappingChunksAreMerged_both() {
        // Prepare
        val linesCollector = LineShellV2Collector(10)
        val flowCollector = ShellCommandOutputFlowCollector()

        // Act
        collectStrings(
            linesCollector, flowCollector,
            listOf("stdout-123456789", "56\nab\ncdefg-stdout"),
            listOf("stderr-123456789", "56\nab\ncdefg-stderr"),
        )

        // Assert
        Assert.assertEquals(listOf("stdout-12345678956", "ab", "cdefg-stdout"), flowCollector.getStdouts())
        Assert.assertEquals(listOf("stderr-12345678956", "ab", "cdefg-stderr"), flowCollector.getStderrs())
    }

    private fun collectStrings(
      linesCollector: LineShellV2Collector,
      flowCollector: FlowCollector<ShellCommandOutputElement>,
      stdout: List<String>,
      stderr: List<String>,
    ) {
        runBlocking {
            linesCollector.start(flowCollector)
            stdout.forEach {
                linesCollector.collectStdout(flowCollector, ByteBufferUtils.stringToByteBuffer(it))
            }
            stderr.forEach {
                linesCollector.collectStderr(flowCollector, ByteBufferUtils.stringToByteBuffer(it))
            }
            linesCollector.end(flowCollector, 0)
        }
    }

    private fun collectStdout(
      linesCollector: LineShellV2Collector,
      flowCollector: FlowCollector<ShellCommandOutputElement>,
      vararg value: String
    ) {
        collectStrings(linesCollector, flowCollector, value.toList(), emptyList())
    }

    private fun collectStderr(
      linesCollector: LineShellV2Collector,
      flowCollector: FlowCollector<ShellCommandOutputElement>,
      vararg value: String
    ) {
        collectStrings(linesCollector, flowCollector, emptyList(), value.toList())
    }

    private class ShellCommandOutputFlowCollector : FlowCollector<ShellCommandOutputElement> {

        val elements = ArrayList<ShellCommandOutputElement>()

        fun getStdouts() = elements.filterIsInstance<StdoutLine>().map { it.contents }

        fun getStderrs() = elements.filterIsInstance<StderrLine>().map { it.contents }

        override suspend fun emit(value: ShellCommandOutputElement) {
            elements.add(value)
        }
    }
}
