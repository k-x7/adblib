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

import com.android.adblib.testingutils.ByteBufferUtils
import com.android.adblib.utils.ByteArrayShellCollector.CommandResult
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
        Assert.assertEquals(listOf(""), flowCollector.stdout)
        Assert.assertEquals(listOf(""), flowCollector.stderr)
        Assert.assertEquals(0, flowCollector.exitCode)
    }

    @Test
    fun testEmptyBytesIsEmptyBytes() {
        // Prepare
        val shellCollector = ByteArrayShellCollector()
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "")

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.stdout)
        Assert.assertEquals(listOf(""), flowCollector.stderr)
        Assert.assertEquals(0, flowCollector.exitCode)
    }

    @Test
    fun test_multipleBuffers() {
        // Prepare
        val shellCollector = ByteArrayShellCollector()
        val flowCollector = BytesFlowCollector()

        // Act
        collect(shellCollector, flowCollector, "a", "b", "c")

        // Assert
        Assert.assertEquals(listOf("abc"), flowCollector.stdout)
        Assert.assertEquals(listOf(""), flowCollector.stderr)
        Assert.assertEquals(0, flowCollector.exitCode)
    }

    @Test
    fun test_error() {
        // Prepare
        val shellCollector = ByteArrayShellCollector()
        val flowCollector = BytesFlowCollector()

        // Act
        collectError(shellCollector, flowCollector, "a", "b", "c")

        // Assert
        Assert.assertEquals(listOf(""), flowCollector.stdout)
        Assert.assertEquals(listOf("abc"), flowCollector.stderr)
        Assert.assertEquals(-1, flowCollector.exitCode)
    }

    private fun collect(
        shellCollector: ByteArrayShellCollector,
        flowCollector: BytesFlowCollector,
        vararg values: String
    ) {
        runBlocking {
            shellCollector.start(flowCollector)
            values.forEach { value ->
                shellCollector.collectStdout(
                    flowCollector,
                    ByteBufferUtils.stringToByteBuffer(value)
                )
            }
            shellCollector.end(flowCollector, 0)
        }
    }

    private fun collectError(
        shellCollector: ByteArrayShellCollector,
        flowCollector: BytesFlowCollector,
        vararg errors: String
    ) {
        runBlocking {
            shellCollector.start(flowCollector)
            errors.forEach { value ->
                shellCollector.collectStderr(
                    flowCollector,
                    ByteBufferUtils.stringToByteBuffer(value)
                )
            }
            shellCollector.end(flowCollector, -1)
        }
    }

    private class BytesFlowCollector : FlowCollector<CommandResult> {

        var stdout = mutableListOf<String>()
        var stderr = mutableListOf<String>()
        var exitCode: Int? = null

        override suspend fun emit(value: CommandResult) {
            // ByteArray does not have equals() and it's also easier to read the test if we convert
            // them to strings.
            stdout.add(String(value.stdout))
            stderr.add(value.stderr)
            exitCode = value.exitCode
        }
    }

}
