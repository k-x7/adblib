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
package com.android.adblib.testing

import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement.ExitCode
import com.android.adblib.ShellCommandOutputElement.StderrLine
import com.android.adblib.ShellCommandOutputElement.StdoutLine
import com.android.adblib.shellAsLines
import com.android.adblib.shellAsText
import com.android.adblib.shellV2AsLines
import com.android.adblib.shellV2AsText
import com.android.adblib.testing.FakeAdbDeviceServices.ShellRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertContentEquals

/**
 * Tests for [FakeAdbDeviceServices]
 */
@Suppress("EXPERIMENTAL_API_USAGE") // runBlocking is experimental
class FakeAdbDeviceServicesTest {

    private val deviceServices = FakeAdbSession().deviceServices
    private val deviceSelector = DeviceSelector.fromSerialNumber("device")

    @Test
    fun shellAsText(): Unit = runBlocking {
        deviceServices.configureShellCommand(deviceSelector, "command", "output")

        val actual = deviceServices.shellAsText(deviceSelector, "command")

        assertEquals("output", actual)
        assertContentEquals(
            listOf(ShellRequest(deviceSelector.toString(), "command")),
            deviceServices.shellRequests
        )
    }

    @Test
    fun shellAsText_withBufferSize(): Unit = runBlocking {
        deviceServices.configureShellCommand(deviceSelector, "command", "command output")

        val actual = deviceServices.shellAsText(deviceSelector, "command", bufferSize = 4)

        assertEquals("command output", actual)
        assertContentEquals(
            listOf(ShellRequest(deviceSelector.toString(), "command", bufferSize = 4)),
            deviceServices.shellRequests
        )
    }

    @Test
    fun shellAsText_runTwice(): Unit = runBlocking {
        deviceServices.configureShellCommand(deviceSelector, "command1", "output")
        deviceServices.configureShellCommand(deviceSelector, "command2", "output")

        deviceServices.shellAsText(deviceSelector, "command1")
        val actual = deviceServices.shellAsText(deviceSelector, "command2")
        assertEquals("output", actual)
        assertContentEquals(
            listOf(
                ShellRequest(deviceSelector.toString(), "command1"),
                ShellRequest(deviceSelector.toString(), "command2"),
            ),
            deviceServices.shellRequests
        )
    }

    @Test
    fun shellAsLines(): Unit = runBlocking {
        deviceServices.configureShellCommand(
            deviceSelector,
            "command",
            """
                line1
                line2
                line3
            """.trimIndent()
        )

        val actual = deviceServices.shellAsLines(deviceSelector, "command").toList()

        assertContentEquals(
            listOf("line1", "line2", "line3"),
            actual,
        )
        assertContentEquals(
            listOf(ShellRequest(deviceSelector.toString(), "command")),
            deviceServices.shellRequests
        )
    }

    @Test
    fun shellV2AsText(): Unit = runBlocking {
        deviceServices.configureShellV2Command(
            deviceSelector,
            "command",
            "output",
            "error",
            exitCode = 1
        )

        val actual = deviceServices.shellV2AsText(deviceSelector, "command")

        assertEquals("output", actual.stdout)
        assertEquals("error", actual.stderr)
        assertEquals(1, actual.exitCode)
        assertContentEquals(
            listOf(ShellRequest(deviceSelector.toString(), "command")),
            deviceServices.shellV2Requests
        )
    }

    @Test
    fun shellV2AsText_withBufferSize(): Unit = runBlocking {
        deviceServices.configureShellV2Command(
            deviceSelector,
            "command",
            "command output",
            "command error",
            exitCode = 1
        )

        val actual = deviceServices.shellV2AsText(deviceSelector, "command", bufferSize = 4)

        assertEquals("command output", actual.stdout)
        assertEquals("command error", actual.stderr)
        assertEquals(1, actual.exitCode)
        assertContentEquals(
            listOf(ShellRequest(deviceSelector.toString(), "command", bufferSize = 4)),
            deviceServices.shellV2Requests
        )
    }

    @Test
    fun shellV2AsText_runTwice(): Unit = runBlocking {
        deviceServices.configureShellV2Command(
            deviceSelector,
            "command",
            "output",
            "error",
            exitCode = 1
        )

        deviceServices.shellV2AsText(deviceSelector, "command")
        val actual = deviceServices.shellV2AsText(deviceSelector, "command")

        assertEquals("output", actual.stdout)
        assertEquals("error", actual.stderr)
        assertEquals(1, actual.exitCode)
        assertContentEquals(
            listOf(
                ShellRequest(deviceSelector.toString(), "command"),
                ShellRequest(deviceSelector.toString(), "command"),
            ),
            deviceServices.shellV2Requests
        )
    }

    @Test
    fun shellV2AsLines(): Unit = runBlocking {
        val stdout = """
          line1
          line2
        """.trimIndent()
        val stderr = """
          error1
          error2
        """.trimIndent()
        deviceServices.configureShellV2Command(
            deviceSelector,
            "command",
            stdout,
            stderr,
            exitCode = 1,
        )

        val actual = deviceServices.shellV2AsLines(deviceSelector, "command").toList()

        assertContentEquals(
            listOf("line1", "line2"),
            actual.filterIsInstance<StdoutLine>().map { it.contents }
        )
        assertContentEquals(
            listOf("error1", "error2"),
            actual.filterIsInstance<StderrLine>().map { it.contents }
        )
        // There is only 1 exit code
        assertContentEquals(
            listOf(1),
            actual.filterIsInstance<ExitCode>().map { it.exitCode }
        )
        // And it's the last entry
        assertEquals(actual.last().javaClass, ExitCode::class.java)
        assertContentEquals(
            listOf(ShellRequest(deviceSelector.toString(), "command")),
            deviceServices.shellV2Requests
        )
    }
}
