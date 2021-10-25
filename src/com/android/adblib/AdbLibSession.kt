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

import com.android.adblib.impl.AdbDeviceServicesImpl
import com.android.adblib.impl.AdbHostServicesImpl
import com.android.adblib.impl.channels.AdbChannelFactoryImpl
import java.util.concurrent.TimeUnit

/**
 * The main entry point of `adblib`, provides access to various service implementations
 * (e.g. [AdbHostServices]) given configuration parameters (e.g. [AdbChannelProvider]) of
 * the host application/consumer.
 */
class AdbLibSession(
    /**
     * The [AdbLibHost] implementation provided by the hosting application for environment
     * specific configuration
     */
    val host: AdbLibHost,
    /**
     * The [AdbChannelProvider] implementation to connect to ADB
     */
    val channelProvider: AdbChannelProvider = AdbChannelProviderFactory.createOpenLocalHost(host),
    /**
     * The timeout (in milliseconds) to use when connecting to ADB
     */
    val connectionTimeoutMillis: Long = TimeUnit.SECONDS.toMillis(30)
) : AutoCloseable {

    private var closed = false

    /**
     * An [AdbChannelFactory] that can be used to create various implementations of
     * [AdbChannel], [AdbInputChannel] and [AdbOutputChannel] for files, streams, etc.
     */
    val channelFactory: AdbChannelFactory = AdbChannelFactoryImpl(host)
        get() {
            throwIfClosed()
            return field
        }

    /**
     * An [AdbHostServices] implementation for this session.
     */
    val hostServices: AdbHostServices = createHostServices()
        get() {
            throwIfClosed()
            return field
        }

    /**
     * An [AdbDeviceServices] implementation for this session.
     */
    val deviceServices: AdbDeviceServices = createDeviceServices()
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
