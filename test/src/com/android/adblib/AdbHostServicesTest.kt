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

import com.android.adblib.impl.AdbHostServicesImpl
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbLibHost
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.hostcommandhandlers.FaultyVersionCommandHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.IOException
import java.util.concurrent.TimeUnit

class AdbHostServicesTest {

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
    fun testVersion() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val hostServices = AdbHostServicesImpl(host, channelProvider, 5_000, TimeUnit.MILLISECONDS)

        // Act
        val internalVersion = runBlocking { hostServices.version() }

        // Assert
        Assert.assertEquals(40, internalVersion)
    }

    @Test
    fun testVersionConnectionFailure() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider())
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val hostServices = AdbHostServicesImpl(host, channelProvider, 5_000, TimeUnit.MILLISECONDS)

        // Act (should throw)
        exceptionRule.expect(IOException::class.java)
        /*val internalVersion = */runBlocking { hostServices.version() }

        // Assert (should not reach this point)
        Assert.fail()
    }

    @Test
    fun testVersionFaultyProtocol() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider())
        fakeAdb.installHostHandler(FaultyVersionCommandHandler.COMMAND) { FaultyVersionCommandHandler() }
        fakeAdb.build().start()
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val hostServices = AdbHostServicesImpl(host, channelProvider, 5_000, TimeUnit.MILLISECONDS)

        // Act (should throw)
        exceptionRule.expect(AdbProtocolErrorException::class.java)
        /*val internalVersion = */runBlocking { hostServices.version() }

        // Assert (should not reach this point)
        Assert.fail()
    }

    @Test
    fun testHostFeaturesWorks() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val hostServices = AdbHostServicesImpl(host, channelProvider, 5_000, TimeUnit.MILLISECONDS)

        // Act
        val featureList = runBlocking { hostServices.hostFeatures() }

        // Assert
        Assert.assertTrue(featureList.contains("shell_v2"))
        Assert.assertTrue(featureList.contains("fixed_push_mkdir"))
        Assert.assertTrue(featureList.contains("push_sync"))
        Assert.assertTrue(featureList.contains("abb_exec"))
    }

    @Test
    fun testDevicesShortFormatWorks() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
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
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val hostServices = AdbHostServicesImpl(host, channelProvider, 5_000, TimeUnit.MILLISECONDS)

        // Act
        val deviceList = runBlocking { hostServices.devices(AdbHostServices.DeviceInfoFormat.SHORT_FORMAT) }

        // Assert
        Assert.assertEquals(1, deviceList.devices.size)
        Assert.assertEquals(0, deviceList.errors.size)
        deviceList.devices[0].let { device ->
            Assert.assertEquals("1234", device.serialNumber)
            Assert.assertEquals(com.android.adblib.DeviceState.ONLINE, device.deviceState)
            Assert.assertNull(device.product)
            Assert.assertNull(device.model)
            Assert.assertNull(device.device)
            Assert.assertNull(device.transportId)
        }
    }

    @Test
    fun testDevicesLongFormatWorks() {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
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
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        val hostServices = AdbHostServicesImpl(host, channelProvider, 5_000, TimeUnit.MILLISECONDS)

        // Act
        val deviceList = runBlocking { hostServices.devices(AdbHostServices.DeviceInfoFormat.LONG_FORMAT) }

        // Assert
        Assert.assertEquals(1, deviceList.devices.size)
        Assert.assertEquals(0, deviceList.errors.size)
        deviceList.devices[0].let { device ->
            Assert.assertEquals("1234", device.serialNumber)
            Assert.assertEquals(com.android.adblib.DeviceState.ONLINE, device.deviceState)
            Assert.assertEquals("test1", device.product)
            Assert.assertEquals("test2", device.model)
            Assert.assertEquals("model", device.device)
            Assert.assertEquals(fakeDevice.transportId.toString(), device.transportId)
        }
    }
}
