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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceInfoTest {

    @Test
    fun fromParserValuesWorks() {
        // Act
        val deviceInfo = DeviceInfo.fromParserValues("1234", "device")

        // Assert
        assertEquals("1234", deviceInfo.serialNumber)
        assertEquals(DeviceState.ONLINE, deviceInfo.deviceState)
        assertEquals("device", deviceInfo.deviceStateString)
        assertNull(deviceInfo.product)
        assertNull(deviceInfo.model)
        assertNull(deviceInfo.device)
        assertNull(deviceInfo.transportId)
    }

    @Test
    fun fromParserValuesMaintainsRawString() {
        // Act
        val deviceInfo = DeviceInfo.fromParserValues("1234", "device-foo")

        // Assert
        assertEquals("1234", deviceInfo.serialNumber)
        assertEquals(DeviceState.UNKNOWN, deviceInfo.deviceState)
        assertEquals("device-foo", deviceInfo.deviceStateString)
        assertNull(deviceInfo.product)
        assertNull(deviceInfo.model)
        assertNull(deviceInfo.device)
        assertNull(deviceInfo.transportId)
    }

    @Test
    fun equalsWorks() {
        // Act
        val deviceInfo = DeviceInfo("1234", DeviceState.OFFLINE)
        val deviceInfo2 = DeviceInfo("1234", DeviceState.OFFLINE)
        val deviceInfo3 = DeviceInfo("1234", DeviceState.OFFLINE,
                                     additionalFields = mapOf(Pair("foo", "bar")))
        val deviceInfo4 = DeviceInfo("1234", DeviceState.OFFLINE,
                                     additionalFields = mapOf(Pair("foo", "bar")))

        // Assert
        assertEquals(deviceInfo, deviceInfo2)
        assertNotEquals(deviceInfo, deviceInfo3)
        assertNotEquals(deviceInfo, deviceInfo3)

        assertNotEquals(deviceInfo2, deviceInfo3)
        assertNotEquals(deviceInfo2, deviceInfo4)

        assertEquals(deviceInfo3, deviceInfo4)
    }

    @Test
    fun copyWorks() {
        // Prepare
        val deviceInfo = DeviceInfo("1234", DeviceState.OFFLINE)

        // Act
        val deviceInfo2 = deviceInfo.copy(deviceState = DeviceState.ONLINE)

        // Assert
        assertNotEquals(deviceInfo, deviceInfo2)
        assertEquals("1234", deviceInfo2.serialNumber)
        assertEquals(DeviceState.ONLINE, deviceInfo2.deviceState)
        assertEquals("device", deviceInfo2.deviceStateString)
        assertNull(deviceInfo.product)
        assertNull(deviceInfo.model)
        assertNull(deviceInfo.device)
        assertNull(deviceInfo.transportId)
    }

    @Test
    fun copyRetainsDeviceStateString() {
        // Prepare
        val deviceInfo = DeviceInfo.fromParserValues("1234", "test-string")

        // Act
        val deviceInfo2 = deviceInfo.copy(deviceState = DeviceState.ONLINE)

        // Assert
        assertNotEquals(deviceInfo, deviceInfo2)
        assertEquals("1234", deviceInfo2.serialNumber)
        assertEquals(DeviceState.ONLINE, deviceInfo2.deviceState)
        assertEquals("test-string", deviceInfo2.deviceStateString)
        assertNull(deviceInfo.product)
        assertNull(deviceInfo.model)
        assertNull(deviceInfo.device)
        assertNull(deviceInfo.transportId)
    }
}
