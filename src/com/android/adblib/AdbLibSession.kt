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

import com.android.adblib.impl.AdbLibSessionImpl
import java.time.Duration

/**
 * Provides access to various ADB services (e.g. [AdbHostServices], [AdbDeviceServices]) for
 * a given [AdbLibHost]. The [close] method should be called when the session is not needed
 * anymore, to ensure all pending operations and all optional state are released.
 *
 * This is the main entry point of `adblib`, use the [create] method to create an instance.
 */
interface AdbLibSession : AutoCloseable {

    /**
     * The [AdbLibHost] implementation provided by the hosting application for environment
     * specific configuration.
     */
    val host: AdbLibHost

    /**
     * An [AdbChannelFactory] that can be used to create various implementations of
     * [AdbChannel], [AdbInputChannel] and [AdbOutputChannel] for files, streams, etc.
     */
    val channelFactory: AdbChannelFactory

    /**
     * An [AdbHostServices] implementation for this session.
     */
    val hostServices: AdbHostServices

    /**
     * An [AdbDeviceServices] implementation for this session.
     */
    val deviceServices: AdbDeviceServices

    companion object {

        /**
         * Creates an instance of an [AdbLibSession] given an [AdbLibHost] instance.
         *
         * @param host The [AdbLibHost] implementation provided by the hosting application for
         *             environment specific configuration
         * @param channelProvider The [AdbChannelProvider] implementation to connect to the ADB server
         * @param connectionTimeout The timeout to use when creating a connection the ADB Server
         */
        @JvmStatic
        fun create(
            host: AdbLibHost,
            channelProvider: AdbChannelProvider = AdbChannelProviderFactory.createOpenLocalHost(host),
            connectionTimeout: Duration = Duration.ofSeconds(30)
        ): AdbLibSession {
            return AdbLibSessionImpl(host, channelProvider, connectionTimeout.toMillis())
        }
    }
}
