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

import org.junit.Assert
import org.junit.Test

class DeviceSelectorTest {

    @Test
    fun hostPrefixWorks() {
        // Prepare
        val device1 = DeviceSelector.fromSerialNumber("12345")
        val device2 = DeviceSelector.fromTransportId("2")
        val device3 = DeviceSelector.local()
        val device4 = DeviceSelector.usb()
        val device5 = DeviceSelector.any()

        // Act

        // Assert
        Assert.assertEquals("host-serial:12345", device1.hostPrefix)
        Assert.assertEquals("host-transport-id:2", device2.hostPrefix)
        Assert.assertEquals("host-local", device3.hostPrefix)
        Assert.assertEquals("host-usb", device4.hostPrefix)
        Assert.assertEquals("host", device5.hostPrefix)
    }

    @Test
    fun transportPrefixWorks() {
        // Prepare
        val device1 = DeviceSelector.fromSerialNumber("12345")
        val device2 = DeviceSelector.fromTransportId("2")
        val device3 = DeviceSelector.local()
        val device4 = DeviceSelector.usb()
        val device5 = DeviceSelector.any()

        // Act

        // Assert
        Assert.assertEquals("host:transport:12345", device1.transportPrefix)
        Assert.assertEquals("host:transport-id:2", device2.transportPrefix)
        Assert.assertEquals("host:transport-local", device3.transportPrefix)
        Assert.assertEquals("host:transport-usb", device4.transportPrefix)
        Assert.assertEquals("host:transport-any", device5.transportPrefix)
    }

    @Test
    fun transportPrefixWithTransportIdWorks() {
        // Prepare
        val device1 = DeviceSelector.withTransportId.fromSerialNumber("12345")
        val device3 = DeviceSelector.withTransportId.local()
        val device4 = DeviceSelector.withTransportId.usb()
        val device5 = DeviceSelector.withTransportId.any()

        // Act

        // Assert
        Assert.assertEquals("host:tport:serial:12345", device1.transportPrefix)
        Assert.assertEquals("host:tport:local", device3.transportPrefix)
        Assert.assertEquals("host:tport:usb", device4.transportPrefix)
        Assert.assertEquals("host:tport:any", device5.transportPrefix)
    }
}
