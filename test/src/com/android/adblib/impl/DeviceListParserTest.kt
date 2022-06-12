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
package com.android.adblib.impl

import com.android.adblib.AdbHostServices
import com.android.adblib.DeviceState
import org.junit.Assert
import org.junit.Test

class DeviceListParserTest {

    @Test
    fun parseShotFormatEmptyOutputWorks() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(AdbHostServices.DeviceInfoFormat.SHORT_FORMAT, "")

        // Assert
        Assert.assertEquals(0, deviceList.size)
        Assert.assertEquals(0, deviceList.errors.size)
    }

    @Test
    fun parseShotFormatEmptyLinesOutputWorks() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(AdbHostServices.DeviceInfoFormat.SHORT_FORMAT, "\n\n\n")

        // Assert
        Assert.assertEquals(0, deviceList.size)
        Assert.assertEquals(0, deviceList.errors.size)
    }

    @Test
    fun parseShotFormatWorks() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(
            AdbHostServices.DeviceInfoFormat.SHORT_FORMAT,
            "HT10X6F12345\tdevice\n" +
                    "adb-FAAY0QWER-jBMEIf._adb-tls-connect._tcp.\tconnecting\n" +
                    "adb-HT10X6F12345-AvY0LF._adb-tls-connect._tcp.\toffline\n" +
                    "emulator-5554\tbootloader\n" +
                    "emulator-5556\thost\n"
        )

        // Assert
        Assert.assertEquals(5, deviceList.size)
        Assert.assertEquals(0, deviceList.errors.size)

        deviceList[0].let { device ->
            Assert.assertEquals("HT10X6F12345", device.serialNumber)
            Assert.assertEquals(DeviceState.ONLINE, device.deviceState)
            Assert.assertSame(DeviceState.ONLINE.state, device.deviceStateString)
            Assert.assertNull(device.product)
            Assert.assertNull(device.model)
            Assert.assertNull(device.device)
            Assert.assertNull(device.transportId)
        }

        deviceList[1].let { device ->
            Assert.assertEquals("adb-FAAY0QWER-jBMEIf._adb-tls-connect._tcp.", device.serialNumber)
            Assert.assertEquals(DeviceState.CONNECTING, device.deviceState)
            Assert.assertSame(DeviceState.CONNECTING.state, device.deviceStateString)
            Assert.assertNull(device.product)
            Assert.assertNull(device.model)
            Assert.assertNull(device.device)
            Assert.assertNull(device.transportId)
        }

        deviceList[2].let { device ->
            Assert.assertEquals("adb-HT10X6F12345-AvY0LF._adb-tls-connect._tcp.", device.serialNumber)
            Assert.assertEquals(DeviceState.OFFLINE, device.deviceState)
            Assert.assertSame(DeviceState.OFFLINE.state, device.deviceStateString)
            Assert.assertNull(device.product)
            Assert.assertNull(device.model)
            Assert.assertNull(device.device)
            Assert.assertNull(device.transportId)
        }

        deviceList[3].let { device ->
            Assert.assertEquals("emulator-5554", device.serialNumber)
            Assert.assertEquals(DeviceState.BOOTLOADER, device.deviceState)
            Assert.assertSame(DeviceState.BOOTLOADER.state, device.deviceStateString)
            Assert.assertNull(device.product)
            Assert.assertNull(device.model)
            Assert.assertNull(device.device)
            Assert.assertNull(device.transportId)
        }

        deviceList[4].let { device ->
            Assert.assertEquals("emulator-5556", device.serialNumber)
            Assert.assertEquals(DeviceState.HOST, device.deviceState)
            Assert.assertSame(DeviceState.HOST.state, device.deviceStateString)
            Assert.assertNull(device.product)
            Assert.assertNull(device.model)
            Assert.assertNull(device.device)
            Assert.assertNull(device.transportId)
        }
    }

    @Test
    fun parseLongFormatEmptyLinesOutputWorks() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(AdbHostServices.DeviceInfoFormat.LONG_FORMAT, "\n\n\n")

        // Assert
        Assert.assertEquals(0, deviceList.size)
        Assert.assertEquals(0, deviceList.errors.size)
    }

    @Test
    fun parseLongFormatWorks() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(
            AdbHostServices.DeviceInfoFormat.LONG_FORMAT,
            "adb-FAAY0QWER-jBMEIf._adb-tls-connect._tcp. device product:crosshatch model:Pixel_3_XL device:crosshatch transport_id:1\n" +
                    "emulator-5554          device product:sdk_gphone_x86 model:Android_SDK_built_for_x86 device:generic_x86 transport_id:3\n" +
                    "emulator-5556          offline transport_id:4\n"
        )

        // Assert
        Assert.assertEquals(3, deviceList.size)
        Assert.assertEquals(0, deviceList.errors.size)

        deviceList[0].let { device ->
            Assert.assertEquals("adb-FAAY0QWER-jBMEIf._adb-tls-connect._tcp.", device.serialNumber)
            Assert.assertEquals(DeviceState.ONLINE, device.deviceState)
            Assert.assertEquals("crosshatch", device.product)
            Assert.assertEquals("Pixel_3_XL", device.model)
            Assert.assertEquals("crosshatch", device.device)
            Assert.assertEquals("1", device.transportId)
        }

        deviceList[1].let { device ->
            Assert.assertEquals("emulator-5554", device.serialNumber)
            Assert.assertEquals(DeviceState.ONLINE, device.deviceState)
            Assert.assertEquals("sdk_gphone_x86", device.product)
            Assert.assertEquals("Android_SDK_built_for_x86", device.model)
            Assert.assertEquals("generic_x86", device.device)
            Assert.assertEquals("3", device.transportId)
        }

        deviceList[2].let { device ->
            Assert.assertEquals("emulator-5556", device.serialNumber)
            Assert.assertEquals(DeviceState.OFFLINE, device.deviceState)
            Assert.assertEquals(null, device.product)
            Assert.assertEquals(null, device.model)
            Assert.assertEquals(null, device.device)
            Assert.assertEquals("4", device.transportId)
        }
    }

    @Test
    fun parseLongFormatWithErrorsWorks() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(
            AdbHostServices.DeviceInfoFormat.LONG_FORMAT,
            "adb-FAAY0QWER-jBMEIf._adb-tls-connect._tcp. device product:crosshatch model:Pixel_3_XL device:crosshatch transport_id:15\n" +
                    "(no serial number)\n" +
                    "emulator-5554          device product:sdk_gphone_x86 model:Android_SDK_built_for_x86 device:generic_x86 transport_id:3\n" +
                    "emulator-5556          offline transport_id:4\n"
        )

        // Assert
        Assert.assertEquals(3, deviceList.size)
        Assert.assertEquals(1, deviceList.errors.size)

        deviceList.errors[0].let { error ->
            Assert.assertEquals("(no serial number)", error.rawLineText)
            Assert.assertEquals(1, error.lineIndex)
        }

        deviceList[0].let { device ->
            Assert.assertEquals(
                "adb-FAAY0QWER-jBMEIf._adb-tls-connect._tcp.",
                device.serialNumber
            )
            Assert.assertEquals(DeviceState.ONLINE, device.deviceState)
            Assert.assertEquals("crosshatch", device.product)
            Assert.assertEquals("Pixel_3_XL", device.model)
            Assert.assertEquals("crosshatch", device.device)
            Assert.assertEquals("15", device.transportId)
        }

        deviceList[1].let { device ->
            Assert.assertEquals("emulator-5554", device.serialNumber)
            Assert.assertEquals(DeviceState.ONLINE, device.deviceState)
            Assert.assertEquals("sdk_gphone_x86", device.product)
            Assert.assertEquals("Android_SDK_built_for_x86", device.model)
            Assert.assertEquals("generic_x86", device.device)
            Assert.assertEquals("3", device.transportId)
        }

        deviceList[2].let { device ->
            Assert.assertEquals("emulator-5556", device.serialNumber)
            Assert.assertEquals(DeviceState.OFFLINE, device.deviceState)
            Assert.assertEquals(null, device.product)
            Assert.assertEquals(null, device.model)
            Assert.assertEquals(null, device.device)
            Assert.assertEquals("4", device.transportId)
        }
    }

    @Test
    fun parseUnknownDeviceStateRetainsRawValue() {
        // Prepare
        val parser = DeviceListParser()

        // Act
        val deviceList = parser.parse(
            AdbHostServices.DeviceInfoFormat.LONG_FORMAT,
            "ser-aaa some-weird-state product:p model:foo device:bar transport_id:1\n"
        )

        // Assert
        Assert.assertEquals(1, deviceList.size)
        Assert.assertEquals(0, deviceList.errors.size)

        deviceList[0].let { device ->
            Assert.assertEquals("ser-aaa", device.serialNumber)
            Assert.assertEquals(DeviceState.UNKNOWN, device.deviceState)
            Assert.assertEquals("some-weird-state", device.deviceStateString)
            Assert.assertEquals("p", device.product)
            Assert.assertEquals("foo", device.model)
            Assert.assertEquals("bar", device.device)
            Assert.assertEquals("1", device.transportId)
        }
    }
}
