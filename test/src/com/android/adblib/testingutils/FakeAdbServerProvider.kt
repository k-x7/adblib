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
package com.android.adblib.testingutils

import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbLibHost
import com.android.adblib.impl.AdbChannelProviderOpenLocalHost
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import java.util.function.Supplier

class FakeAdbServerProvider : AutoCloseable {

    val port: Int
        get() = server?.port ?: 0

    private val builder = FakeAdbServer.Builder()
    private var server: FakeAdbServer? = null

    fun buildDefault(): FakeAdbServerProvider {
        // Build the server and configure it to use the default ADB command handlers.
        builder.installDefaultCommandHandlers()
        build()
        return this
    }

    fun installHostHandler(
        command: String,
        handlerConstructor: Supplier<HostCommandHandler?>
    ): FakeAdbServerProvider {
        builder.setHostCommandHandler(command, handlerConstructor)
        return this
    }

    fun build(): FakeAdbServerProvider {
        server = builder.build()
        return this
    }

    fun start(): FakeAdbServerProvider {
        server?.start()
        return this
    }

    fun createChannelProvider(host: AdbLibHost): AdbChannelProvider {
        return AdbChannelProviderOpenLocalHost(host, portSupplier = { port })
    }

    override fun close() {
        server?.close()
    }
}
