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
package com.android.adblib

import com.android.adblib.impl.AdbDeviceServicesImpl
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbLibHost
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.IOException
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class AdbDeviceServicesTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun testShell() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val device = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        val bytes = runBlocking {
            deviceServices.shell(deviceSelector, "getprop", collector).first()
        }

        // Assert
        Assert.assertNull(collector.transportId)
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [sdk]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, AdbProtocolUtils.byteBufferToString(bytes))
    }

    @Test
    fun testShellToText() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val device = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val commandOutput = runBlocking {
            deviceServices.shellAsText(deviceSelector, "getprop")
        }

        // Assert
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [sdk]

        """.trimIndent()
        Assert.assertEquals(expectedOutput, commandOutput)
    }

    @Test
    fun testShellToLines() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val device = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val lines = runBlocking {
            deviceServices.shellAsLines(deviceSelector, "getprop", bufferSize = 10).toList()
        }

        // Assert
        val expectedOutput = """
            # This is some build info
            # This is more build info

            [ro.product.manufacturer]: [test1]
            [ro.product.model]: [test2]
            [ro.build.version.release]: [model]
            [ro.build.version.sdk]: [sdk]

        """.trimIndent()
        Assert.assertEquals(expectedOutput.lines(), lines)
    }

    @Test
    fun testShellWithArguments() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val device = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)

        // Act
        val commandOutput = runBlocking {
            deviceServices.shellAsText(deviceSelector, "cmd package install-create")
        }

        // Assert
        val expectedOutput = "Success: created install session [1234]"
        Assert.assertEquals(expectedOutput, commandOutput)
    }

    @Test
    fun testShellWithTimeout() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val device = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(device.deviceId)
        val collector = ByteBufferShellCollector()

        // Act
        exceptionRule.expect(TimeoutException::class.java)
        /*val bytes = */runBlocking {
            deviceServices.shell(
                deviceSelector,
                "write-no-stop",
                collector,
                null,
                Duration.ofMillis(10)
            ).first()
        }

        // Assert
        Assert.fail("Should not be reached")
    }

    @Test
    fun testShellWithStdin() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            This is some text with
            split in multiple lines
            and containing non-ascii
            characters such as
            - ඒ (SINHALA LETTER EEYANNA (U+0D92))
            - ⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent()

        // Act
        val commandOutput = runBlocking {
            deviceServices.shellAsText(
                deviceSelector,
                "cat",
                stdinChannel = input.asAdbInputChannel(host)
            )
        }

        // Assert
        Assert.assertEquals(input, commandOutput)
    }

    @Test
    fun testShellWithLargeInputAndSmallBufferSize() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val input = """
            This is some text with
            split in multiple lines
            and containing non-ascii
            characters such as
            - ඒඒඒඒඒඒ (SINHALA LETTER EEYANNA (U+0D92))
            - ⿉⿉⿉⿉⿉⿉⿉ (KANGXI RADICAL MILLET (U+2FC9))

        """.trimIndent().repeat(100)
        // To ensure we don't spam the log during this test
        host.logger.minLevel = host.logger.minLevel.coerceAtLeast(AdbLogger.Level.INFO)

        // Act
        val commandOutput = runBlocking {
            deviceServices.shellAsText(
                deviceSelector,
                "cat",
                input.asAdbInputChannel(host),
                INFINITE_DURATION,
                15
            )
        }

        // Assert
        Assert.assertEquals(input, commandOutput)
    }

    @Test
    fun testShellWithErroneousStdinMaintainsInitialException() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)
        val errorInputChannel = object : AdbInputChannel {
            private var firstCall = true
            override suspend fun read(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
                if (firstCall) {
                    firstCall = false
                    buffer.put('a'.toByte())
                    buffer.put('a'.toByte())
                    return 2
                } else {
                    throw MyTestException("hello")
                }
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        exceptionRule.expect(MyTestException::class.java)
        /*val ignored = */runBlocking {
            deviceServices.shellAsText(deviceSelector, "cat", stdinChannel = errorInputChannel)
        }

        // Assert
        Assert.fail() // Should not reach
    }

    /**
     * Ensures the [Flow] returned by [AdbDeviceServices.shellAsLines] has the expected "streaming"
     * behavior, i.e. that lines of output are emitted to the [Flow] as soon as they are received
     * from the shell output (`stdin` in this case).
     *
     * This test could fail as an infinite wait if the input channel was read "ahead" before
     * the shell output is passed to the underlying [ShellCollector] and [Flow].
     * This could easily happen if the [AdbDeviceServices.shell] implementation had the
     * coroutines forwarding stdin and stdout run inside coroutine scopes that wait on each other,
     * e.g. parent/child scopes.
     */
    @Test
    fun testShellStdinIsForwardedConcurrentlyWithStdout() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = addFakeDevice(fakeAdb)
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val deviceServices = createDeviceServices(host, channelProvider)
        val deviceSelector = DeviceSelector.fromSerialNumber(fakeDevice.deviceId)

        // Channel used to coordinate our custom AdbInputChannel and consuming the flow
        val inputOutputCoordinator = Channel<String>(1)

        // AdbInputChannel implementation that simulates a input of 10 lines
        val testInputChannel = object : AdbInputChannel {
            val lineCount = 10
            var currentLineIndex = 0
            override suspend fun read(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
                // Wait until we are given "go-go"
                inputOutputCoordinator.receive()

                // Read lineCount lines, then EOF
                if (currentLineIndex >= lineCount) {
                    return -1
                }

                val bytes =
                    "line ${currentLineIndex + 1}\n".toByteArray(AdbProtocolUtils.ADB_CHARSET)
                buffer.put(bytes)
                currentLineIndex++
                return bytes.size
            }

            override fun close() {
                // Nothing
            }
        }

        // Act
        val flow = deviceServices.shellAsLines(deviceSelector, "cat", testInputChannel)

        // Assert
        Assert.assertEquals(
            "Input should be left alone before the flow is active",
            0, testInputChannel.currentLineIndex,
        )
        runBlocking {
            // Tell test input channel to process one `read` request
            inputOutputCoordinator.send("go-go")

            // Collect all text lines from the flow, while verifying our custom AdbInputChannel
            // is read from concurrently
            flow.collectIndexed { index, line ->
                if (index == 10) {
                    // Last line is empty, since there is a trailing '\n' in the previous line
                    Assert.assertEquals(
                        "Last line index should be 10",
                        10, testInputChannel.currentLineIndex
                    )
                    Assert.assertEquals(
                        "Last line should be empty",
                        "", line
                    )
                } else {
                    Assert.assertEquals(
                        "Input channel should advance one line at a time",
                        index + 1, testInputChannel.currentLineIndex
                    )
                    Assert.assertEquals("line ${index + 1}", line)
                }

                // Tell test input channel to process one `read` request
                inputOutputCoordinator.send("go-go")
            }
        }
    }

    private fun createDeviceServices(
        host: TestingAdbLibHost,
        channelProvider: FakeAdbServerProvider.TestingChannelProvider
    ): AdbDeviceServicesImpl {
        return AdbDeviceServicesImpl(
            host,
            channelProvider,
            SOCKET_CONNECT_TIMEOUT_MS,
            TimeUnit.MILLISECONDS
        )
    }

    private fun addFakeDevice(fakeAdb: FakeAdbServerProvider): DeviceState {
        val fakeDevice =
            fakeAdb.connectDevice(
                "1234",
                "test1",
                "test2",
                "model",
                "sdk",
                DeviceState.HostConnectionType.USB
            )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        return fakeDevice
    }

    class ByteBufferShellCollector : ShellCollector<ByteBuffer> {

        private val buffer = ResizableBuffer()
        var transportId: Long? = null

        override suspend fun start(collector: FlowCollector<ByteBuffer>, transportId: Long?) {
            this.transportId = transportId
        }

        override suspend fun collect(collector: FlowCollector<ByteBuffer>, stdout: ByteBuffer) {
            buffer.appendBytes(stdout)
        }

        override suspend fun end(collector: FlowCollector<ByteBuffer>) {
            collector.emit(buffer.forChannelWrite())
        }
    }

    class MyTestException(message: String) : IOException(message)
}
