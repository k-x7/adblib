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

import com.android.adblib.BatchShellCommandOutputElement
import com.android.adblib.BatchShellCommandOutputElement.StderrLine
import com.android.adblib.BatchShellCommandOutputElement.StdoutLine
import com.android.adblib.LineBatchShellV2Collector
import com.android.adblib.testingutils.ByteBufferUtils
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class LineBatchShellV2CollectorTest {

    @Test
    fun oneBatch_withoutTrailingNewline_stdout() {
        // Prepare
        val linesCollector = LineBatchShellV2Collector()
        val flowCollector = BatchShellCommandOutputFlowCollector()

        // Act
        collectStdout(
            linesCollector, flowCollector,
            """
                line1
                line2
                line3
            """.trimIndent()
        )

        // Assert
        Assert.assertEquals(
            listOf(listOf("line1", "line2"), listOf("line3")),
            flowCollector.getStdouts()
        )
    }

    @Test
    fun oneBatch_withTrailingNewline_stdout() {
        // Prepare
        val linesCollector = LineBatchShellV2Collector()
        val flowCollector = BatchShellCommandOutputFlowCollector()

        // Act
        collectStdout(
            linesCollector, flowCollector,
            """
                line1
                line2
                line3

            """.trimIndent()
        )

        // Assert
        Assert.assertEquals(
            listOf(listOf("line1", "line2", "line3"), listOf("")),
            flowCollector.getStdouts()
        )
    }

    @Test
    fun multipleBatches_stdout() {
        // Prepare
        val linesCollector = LineBatchShellV2Collector()
        val flowCollector = BatchShellCommandOutputFlowCollector()

        // Act
        collectStdout(
            linesCollector, flowCollector,
            """
                line1
                line2
                lin
            """.trimIndent(),
            """
                e3
                line4
                line5
            """.trimIndent()
        )

        // Assert
        Assert.assertEquals(
            listOf(
                listOf("line1", "line2"),
                listOf("line3", "line4"),
                listOf("line5"),
            ),
            flowCollector.getStdouts()
        )
    }

    @Test
    fun oneBatch_withoutTrailingNewline_stderr() {
        // Prepare
        val linesCollector = LineBatchShellV2Collector()
        val flowCollector = BatchShellCommandOutputFlowCollector()

        // Act
        collectStderr(
            linesCollector, flowCollector,
            """
                line1
                line2
                line3
            """.trimIndent()
        )

        // Assert
        Assert.assertEquals(
            listOf(listOf("line1", "line2"), listOf("line3")),
            flowCollector.getStderrs()
        )
    }

    @Test
    fun oneBatch_withTrailingNewline_stderr() {
        // Prepare
        val linesCollector = LineBatchShellV2Collector()
        val flowCollector = BatchShellCommandOutputFlowCollector()

        // Act
        collectStderr(
            linesCollector, flowCollector,
            """
                line1
                line2
                line3

            """.trimIndent()
        )

        // Assert
        Assert.assertEquals(
            listOf(listOf("line1", "line2", "line3"), listOf("")),
            flowCollector.getStderrs()
        )
    }

    @Test
    fun multipleBatches_stderr() {
        // Prepare
        val linesCollector = LineBatchShellV2Collector()
        val flowCollector = BatchShellCommandOutputFlowCollector()

        // Act
        collectStderr(
            linesCollector, flowCollector,
            """
                line1
                line2
                lin
            """.trimIndent(),
            """
                e3
                line4
                line5
            """.trimIndent()
        )

        // Assert
        Assert.assertEquals(
            listOf(
                listOf("line1", "line2"),
                listOf("line3", "line4"),
                listOf("line5"),
            ),
            flowCollector.getStderrs()
        )
    }

    private fun collectStrings(
      linesCollector: LineBatchShellV2Collector,
      flowCollector: FlowCollector<BatchShellCommandOutputElement>,
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
      linesCollector: LineBatchShellV2Collector,
      flowCollector: FlowCollector<BatchShellCommandOutputElement>,
      vararg value: String
    ) {
        collectStrings(linesCollector, flowCollector, value.toList(), emptyList())
    }

    private fun collectStderr(
      linesCollector: LineBatchShellV2Collector,
      flowCollector: FlowCollector<BatchShellCommandOutputElement>,
      vararg value: String
    ) {
        collectStrings(linesCollector, flowCollector, emptyList(), value.toList())
    }

    private class BatchShellCommandOutputFlowCollector : FlowCollector<BatchShellCommandOutputElement> {

        val elements = ArrayList<BatchShellCommandOutputElement>()

        fun getStdouts() = elements.filterIsInstance<StdoutLine>().map { it.lines }

        fun getStderrs() = elements.filterIsInstance<StderrLine>().map { it.lines }

        override suspend fun emit(value: BatchShellCommandOutputElement) {
            elements.add(value)
        }
    }
}
