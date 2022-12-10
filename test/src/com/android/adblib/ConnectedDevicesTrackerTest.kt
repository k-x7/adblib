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
package com.android.adblib

import com.android.adblib.impl.ConnectedDevicesTrackerImpl
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.fakeadbserver.DeviceState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.time.Duration

class ConnectedDevicesTrackerTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    data class TestKey(val id: String) : CoroutineScopeCache.Key<Any>("test key $id")

    @Test
    fun constructorDoesNotStartTracking() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)

        // Act
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        delay(500)

        // Assert
        Assert.assertEquals(0, deviceCacheManager.connectedDevices.value.size)
    }

    @Test
    fun startWorks() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)

        // Act
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isNotEmpty()
        }

        // Assert
        Assert.assertEquals(1, deviceCacheManager.connectedDevices.value.size)
    }

    @Test
    fun closingSessionEndsStateFlow() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)

        // Act
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isNotEmpty()
        }
        session.close()
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isEmpty()
        }

        // Assert
        Assert.assertTrue(deviceCacheManager.connectedDevices.value.isEmpty())

    }

    @Test
    fun connectedDeviceShowsInStateFlow() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)

        // Act
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isNotEmpty()
        }

        // Assert
        Assert.assertEquals(1, deviceCacheManager.connectedDevices.value.size)
        val connectedDevice = deviceCacheManager.connectedDevices.value[0]
        Assert.assertEquals("1234", connectedDevice.serialNumber)
        Assert.assertEquals(
            com.android.adblib.DeviceState.ONLINE,
            connectedDevice.deviceInfo.deviceState
        )
    }

    @Test
    fun connectedDeviceShowsChangingDeviceStateInStateFlow() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)

        // Act
        val deviceInfoList = mutableListOf<DeviceInfo>()
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isNotEmpty()
        }
        val connectedDevice = deviceCacheManager.connectedDevices.value.first()

        // collect ONLINE state
        deviceInfoList.add(connectedDevice.deviceInfoFlow.value)

        // collect UNAUTHORIZED state
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.UNAUTHORIZED
        yieldUntil {
            deviceInfoList.last() != connectedDevice.deviceInfoFlow.value
        }
        deviceInfoList.add(connectedDevice.deviceInfoFlow.value)

        // collect RECOVERY state
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.RECOVERY
        yieldUntil {
            deviceInfoList.last() != connectedDevice.deviceInfoFlow.value
        }
        deviceInfoList.add(connectedDevice.deviceInfoFlow.value)

        // Assert
        Assert.assertEquals(3, deviceInfoList.size)
        Assert.assertEquals(
            listOf(
                com.android.adblib.DeviceState.ONLINE,
                com.android.adblib.DeviceState.UNAUTHORIZED,
                com.android.adblib.DeviceState.RECOVERY
            ),
            deviceInfoList.map { it.deviceState }.toList()
        )
    }

    @Test
    fun connectedDeviceBecomesInactiveWhenDeviceIsDisconnected() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val cacheKey = TestKey("foo")
        val session = createSession(fakeAdb)

        // Act
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isNotEmpty()
        }
        val connectedDevice = deviceCacheManager.connectedDevices.value.first()
        connectedDevice.cache.getOrPut(cacheKey) { 12 }
        fakeAdb.disconnectDevice(fakeDevice.deviceId)
        yieldUntil {
            deviceCacheManager.connectedDevices.value.isEmpty()
        }

        // Assert
        Assert.assertEquals("1234", connectedDevice.serialNumber)
        Assert.assertFalse(connectedDevice.scope.isActive)
        Assert.assertEquals(
            com.android.adblib.DeviceState.OFFLINE,
            connectedDevice.deviceInfoFlow.value.deviceState
        )
        Assert.assertEquals(10, connectedDevice.cache.getOrPut(cacheKey) { 10 })
        Assert.assertFalse(connectedDevice.cache.scope.isActive)
    }

    @Test
    fun deviceCacheCreatesCache() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        val key = TestKey("foo")

        // Act
        yieldUntil {
            deviceCacheManager.connectedDevices.value.size == 1
        }
        val cache = deviceCacheManager.deviceCache(fakeDevice.deviceId)
        val value1 = cache.getOrPut(key) { 10 }
        val value2 = cache.getOrPut(key) { 11 }
        val value3 = cache.getOrPut(key) { 12 }

        // Assert
        Assert.assertEquals(10, value1)
        Assert.assertEquals(10, value2)
        Assert.assertEquals(10, value3)
    }

    @Test
    fun deviceCacheReturnsNoOpCacheForUnknownDevice() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val session = createSession(fakeAdb)
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        val key = TestKey("foo")

        // Act
        val cache = deviceCacheManager.deviceCache("2345")
        val value1 = cache.getOrPut(key) { 10 }
        val value2 = cache.getOrPut(key) { 11 }
        val value3 = cache.getOrPut(key) { 12 }

        // Assert
        Assert.assertEquals(10, value1)
        Assert.assertEquals(11, value2)
        Assert.assertEquals(12, value3)
    }

    @Test
    fun deviceCacheIsClosedWhenDeviceDisconnected() = runBlockingWithTimeout {
        // Prepare
        val fakeAdb = registerCloseable(FakeAdbServerProvider().buildDefault().start())
        val fakeDevice = fakeAdb.connectDevice(
            "1234",
            "test1",
            "test2",
            "model",
            "sdk",
            DeviceState.HostConnectionType.USB
        )
        fakeDevice.deviceStatus = DeviceState.DeviceStatus.ONLINE
        val session = createSession(fakeAdb)
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session)
        val key = TestKey("foo")
        val closeable = object : AutoCloseable {
            var closed = false
            override fun close() {
                closed = true
            }
        }

        // Act
        yieldUntil {
            deviceCacheManager.connectedDevices.value.size == 1
        }
        val deviceCache = deviceCacheManager.deviceCache(fakeDevice.deviceId)
        deviceCache.getOrPut(key) { closeable }
        fakeAdb.disconnectDevice(fakeDevice.deviceId)
        yieldUntil {
            closeable.closed
        }
        delay(100)

        // Assert
        Assert.assertFalse(deviceCache.scope.isActive)
        Assert.assertTrue(closeable.closed)
        Assert.assertEquals(0, deviceCacheManager.connectedDevices.value.size)
    }

    private fun createSession(fakeAdb: FakeAdbServerProvider): AdbSession {
        val host = registerCloseable(TestingAdbSessionHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        return AdbSession.create(
            host,
            channelProvider,
            Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
        )
    }
}
