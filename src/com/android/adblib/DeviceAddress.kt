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

import java.net.InetSocketAddress

/**
 * An abstraction over a device address, typically of the form `host:port`, similar to a
 * [InetSocketAddress].
 *
 * To ensure future compatibility, converting from/to an [InetSocketAddress] is supported but
 * not guaranteed by future implementations. However, if the address format is irrelevant to the
 * consumer, [DeviceAddress] allows round-tripping an arbitrary address from/to the ADB server
 * without relying on the specific format of the address.
 */
class DeviceAddress(
    /**
     * The [String] representation of the [DeviceAddress], typically in the form `host:port`.
     */
    val address: String) {

    /**
     * Returns an [InetSocketAddress.isUnresolved] [InetSocketAddress] for this [DeviceAddress]
     *
     * @throws IllegalArgumentException if this [DeviceAddress] is not compatible with an
     *                 [InetSocketAddress] representation.
     */
    fun toInetAddress(): InetSocketAddress {
        // deviceAddress is a hostname:port string
        val portSeparatorIndex = address.lastIndexOf(":")
        val (host, port) = if (portSeparatorIndex < 0) {
            Pair(address, 0)
        }
        else {
            Pair(address.substring(0, portSeparatorIndex), address.substring(portSeparatorIndex + 1).toInt())
        }
        return InetSocketAddress.createUnresolved(host, port)
    }

    /**
     * Returns a string representation of the device address.
     *
     * @see [address]
     */
    override fun toString(): String {
        return address
    }
}

fun InetSocketAddress.toDeviceInetAddress(): DeviceAddress {
    return DeviceAddress("$hostString:$port")
}
