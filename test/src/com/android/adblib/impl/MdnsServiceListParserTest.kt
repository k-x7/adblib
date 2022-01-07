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

import org.junit.Assert
import org.junit.Test

class MdnsServiceListParserTest {

    @Test
    fun parseEmptyOutputWorks() {
        // Prepare
        val parser = MdnsServiceListParser()

        // Act
        val serviceList = parser.parse("")

        // Assert
        Assert.assertEquals(0, serviceList.size)
        Assert.assertEquals(0, serviceList.errors.size)
    }

    @Test
    fun parseEmptyLinesOutputWorks() {
        // Prepare
        val parser = MdnsServiceListParser()

        // Act
        val serviceList = parser.parse("\n\n\n")

        // Assert
        Assert.assertEquals(0, serviceList.size)
        Assert.assertEquals(0, serviceList.errors.size)
    }

    @Test
    fun parseWorks() {
        // Prepare
        val parser = MdnsServiceListParser()

        // Act
        val serviceList = parser.parse(
            "adb-8AAY0GYQW-jBMEIf\t_adb-tls-connect._tcp.\t192.168.1.154:38103\n" +
                    "adb-HT75B1A00212-AvY0LF\t_adb-tls-connect._tcp.\t192.168.1.90:42855\n" +
                    "some random text\n" +
                    "some-serial\tinvalid-ip\t192.aaa:42941\n" +
                    "adb-939AX05XBZ-vWgJpq\t_adb-tls-connect._tcp.\t192.168.1.174:42941\n" +
                    "adb-939AX05XBZ-vWgJpq\t_adb-tls-pairing._tcp.\t192.168.1.174:40711\n"
        )

        // Assert
        Assert.assertEquals(5, serviceList.size)
        Assert.assertEquals(1, serviceList.errors.size)

        serviceList[0].let { service ->
            Assert.assertEquals("adb-8AAY0GYQW-jBMEIf", service.instanceName)
            Assert.assertEquals("_adb-tls-connect._tcp.", service.serviceName)
            Assert.assertEquals("192.168.1.154:38103", service.deviceAddress.address)
        }

        serviceList[1].let { service ->
            Assert.assertEquals("adb-HT75B1A00212-AvY0LF", service.instanceName)
            Assert.assertEquals("_adb-tls-connect._tcp.", service.serviceName)
            Assert.assertEquals("192.168.1.90:42855", service.deviceAddress.address)
        }

        serviceList[2].let { service ->
            Assert.assertEquals("some-serial", service.instanceName)
            Assert.assertEquals("invalid-ip", service.serviceName)
            Assert.assertEquals("192.aaa:42941", service.deviceAddress.address)
        }

        serviceList[3].let { service ->
            Assert.assertEquals("adb-939AX05XBZ-vWgJpq", service.instanceName)
            Assert.assertEquals("_adb-tls-connect._tcp.", service.serviceName)
            Assert.assertEquals("192.168.1.174:42941", service.deviceAddress.address)
        }

        serviceList[4].let { service ->
            Assert.assertEquals("adb-939AX05XBZ-vWgJpq", service.instanceName)
            Assert.assertEquals("_adb-tls-pairing._tcp.", service.serviceName)
            Assert.assertEquals("192.168.1.174:40711", service.deviceAddress.address)
        }

        serviceList.errors[0].let { errorInfo ->
            Assert.assertNotNull(errorInfo.message)
            Assert.assertEquals(2, errorInfo.lineIndex)
            Assert.assertEquals("some random text", errorInfo.rawLineText)
        }
    }
}
