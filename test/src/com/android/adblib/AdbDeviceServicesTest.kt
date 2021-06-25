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
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

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

    private fun createDeviceServices(
        host: TestingAdbLibHost,
        channelProvider: FakeAdbServerProvider.TestingChannelProvider
    ): AdbDeviceServicesImpl {
        val deviceServices =
            AdbDeviceServicesImpl(
                host,
                channelProvider,
                SOCKET_CONNECT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS
            )
        return deviceServices
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
}
