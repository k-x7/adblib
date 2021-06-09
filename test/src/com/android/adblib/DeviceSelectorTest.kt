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
}
