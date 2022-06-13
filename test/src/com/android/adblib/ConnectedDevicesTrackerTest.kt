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
import com.android.adblib.testingutils.TestingAdbLibHost
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
        Assert.assertEquals(0, deviceCacheManager.deviceCount)
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
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session).also { it.start() }
        yieldUntil {
            deviceCacheManager.deviceCount > 0
        }

        // Assert
        Assert.assertEquals(1, deviceCacheManager.deviceCount)
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
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session).also { it.start() }
        val key = TestKey("foo")

        // Act
        yieldUntil {
            deviceCacheManager.deviceCount == 1
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
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session).also { it.start() }
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
        val deviceCacheManager = ConnectedDevicesTrackerImpl(session).also { it.start() }
        val key = TestKey("foo")
        val closeable = object : AutoCloseable {
            var closed = false
            override fun close() {
                closed = true
            }
        }

        // Act
        yieldUntil {
            deviceCacheManager.deviceCount == 1
        }
        val deviceCache = deviceCacheManager.deviceCache(fakeDevice.deviceId)
        deviceCache.getOrPut(key) { closeable }
        fakeAdb.disconnectDevice(fakeDevice.deviceId)
        yieldUntil {
            closeable.closed
        }

        // Assert
        Assert.assertFalse(deviceCache.scope.isActive)
        Assert.assertTrue(closeable.closed)
        Assert.assertEquals(0, deviceCacheManager.deviceCount)
    }

    private fun createSession(fakeAdb: FakeAdbServerProvider): AdbLibSession {
        val host = registerCloseable(TestingAdbLibHost())
        val channelProvider = fakeAdb.createChannelProvider(host)
        return AdbLibSession.create(
            host,
            channelProvider,
            Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
        )
    }
}
