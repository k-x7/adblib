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

import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbLibSession
import com.android.adblib.impl.channels.AdbChannelFactoryImpl
import java.util.concurrent.TimeUnit

internal class AdbLibSessionImpl(
    override val host: AdbLibHost,
    val channelProvider: AdbChannelProvider,
    private val connectionTimeoutMillis: Long
) : AdbLibSession {

    private var closed = false

    override val channelFactory: AdbChannelFactory = AdbChannelFactoryImpl(host)
        get() {
            throwIfClosed()
            return field
        }

    override val hostServices: AdbHostServices = createHostServices()
        get() {
            throwIfClosed()
            return field
        }

    override val deviceServices: AdbDeviceServices = createDeviceServices()
        get() {
            throwIfClosed()
            return field
        }

    override fun close() {
        //TODO: Figure out if it would be worthwhile and efficient enough to implement a
        //      way to track and release all resources acquired from this session. For example,
        //      we may want to close all connections to the ADB server that were opened
        //      from this session.
        host.logger.debug { "Closing session" }
        closed = true
    }

    private fun createHostServices(): AdbHostServices {
        return AdbHostServicesImpl(
            host,
            channelProvider,
            connectionTimeoutMillis,
            TimeUnit.MILLISECONDS
        )
    }

    private fun createDeviceServices(): AdbDeviceServices {
        return AdbDeviceServicesImpl(
            host,
            channelProvider,
            connectionTimeoutMillis,
            TimeUnit.MILLISECONDS
        )
    }

    private fun throwIfClosed() {
        if (closed) {
            throw IllegalStateException("Session has been closed")
        }
    }
}
