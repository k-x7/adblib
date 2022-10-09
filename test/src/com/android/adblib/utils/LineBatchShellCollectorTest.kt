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

import com.android.adblib.LineBatchShellCollector
import com.android.adblib.testingutils.ByteBufferUtils
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Test

class LineBatchShellCollectorTest {

    @Test
    fun oneBatch_withoutTrailingNewline() {
        // Prepare
        val linesCollector = LineBatchShellCollector()
        val flowCollector = LineBatchFlowCollector()

        // Act
        collectStrings(
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
            flowCollector.lineBatches
        )
    }

    @Test
    fun oneBatch_withTrailingNewline() {
        // Prepare
        val linesCollector = LineBatchShellCollector()
        val flowCollector = LineBatchFlowCollector()

        // Act
        collectStrings(
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
            flowCollector.lineBatches
        )
    }

    @Test
    fun multipleBatches() {
        // Prepare
        val linesCollector = LineBatchShellCollector()
        val flowCollector = LineBatchFlowCollector()

        // Act
        collectStrings(
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
            flowCollector.lineBatches
        )
    }

    private fun collectStrings(
      linesCollector: LineBatchShellCollector,
      flowCollector: FlowCollector<List<String>>,
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

    private class LineBatchFlowCollector : FlowCollector<List<String>> {

        val lineBatches = ArrayList<List<String>>()

        override suspend fun emit(value: List<String>) {
            lineBatches.add(value)
        }
    }
}
