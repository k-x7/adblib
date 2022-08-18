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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.net.InetAddress
import java.net.InetSocketAddress

class DeviceAddressTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun fromUnresolvedInetSocketAddressWorks() {
        // Prepare
        val inetAddress = InetSocketAddress.createUnresolved("foo.bar", 1000)

        // Act
        val deviceAddress = inetAddress.toDeviceInetAddress()

        // Assert
        assertEquals("foo.bar:1000", deviceAddress.address)
    }

    @Test
    fun fromResolvedInetSocketAddressWorks() {
        // Prepare
        val inetAddress = InetSocketAddress(InetAddress.getByName("10.0.0.1"), 1000)

        // Act
        val deviceAddress = inetAddress.toDeviceInetAddress()

        // Assert
        assertEquals("10.0.0.1:1000", deviceAddress.address)
    }

    @Test
    fun toInetSocketAddressWorks() {
        // Prepare
        val deviceAddress = DeviceAddress("foo.bar:1000")

        // Act
        val inetAddress = deviceAddress.toInetAddress()

        // Assert
        assertEquals(InetSocketAddress.createUnresolved("foo.bar", 1000), inetAddress)
        assertEquals("foo.bar", inetAddress.hostString)
        assertEquals(1000, inetAddress.port)
    }

    @Test
    fun wrappingInvalidAddressWorks() {
        // Prepare
        val expected = "a k b l :: --200"

        // Act
        val deviceAddress = DeviceAddress(expected)

        // Assert
        assertEquals(expected, deviceAddress.address)
    }

    @Test
    fun convertingInvalidAddressThrows() {
        // Prepare
        val expected = "a k b l :: --200"
        val deviceAddress = DeviceAddress(expected)

        // Act
        exceptionRule.expect(IllegalArgumentException::class.java)
        deviceAddress.toInetAddress()
    }
}
