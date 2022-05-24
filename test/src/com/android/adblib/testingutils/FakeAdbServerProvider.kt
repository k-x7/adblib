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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbLibHost
import com.android.adblib.impl.channels.AdbSocketChannelImpl
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.DeviceState.HostConnectionType
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.MdnsService
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.hostcommandhandlers.HostCommandHandler
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Timeout for fake adb server APIs that go through the server's internal
 * sequential executor. In most cases, API calls take only a few milliseconds,
 * but the time can dramatically increase under stress testing.
 */
val FAKE_ADB_SERVER_EXECUTOR_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2)

class FakeAdbServerProvider : AutoCloseable {

    val inetAddress: InetAddress
        get() = server?.inetAddress ?: throw IllegalStateException("Server not started")

    val port: Int
        get() = server?.port ?: 0

    val socketAddress: InetSocketAddress
        get() = InetSocketAddress(inetAddress, port)

    private val builder = FakeAdbServer.Builder()
    private var server: FakeAdbServer? = null

    fun buildDefault(): FakeAdbServerProvider {
        // Build the server and configure it to use the default ADB command handlers.
        builder.installDefaultCommandHandlers()
        build()
        return this
    }

    fun buildWithFeatures(features : Set<String>) : FakeAdbServerProvider {
        // Build the server and configure it to use the default ADB command handlers.
        builder.installDefaultCommandHandlers()
        builder.setFeatures(features)
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

    fun installDeviceHandler(handler: DeviceCommandHandler): FakeAdbServerProvider {
        builder.addDeviceHandler(handler)
        return this
    }

    fun build(): FakeAdbServerProvider {
        server = builder.build()
        return this
    }

    fun connectDevice(
        deviceId: String,
        manufacturer: String,
        deviceModel: String,
        release: String,
        sdk: String,
        hostConnectionType: HostConnectionType
    ): DeviceState {
        return server?.connectDevice(
            deviceId,
            manufacturer,
            deviceModel,
            release,
            sdk,
            hostConnectionType
        )?.get(FAKE_ADB_SERVER_EXECUTOR_TIMEOUT_MS, TimeUnit.MILLISECONDS) ?: throw IllegalArgumentException()
    }

    fun addMdnsService(service: MdnsService) {
        server?.addMdnsService(service)?.get(FAKE_ADB_SERVER_EXECUTOR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    fun start(): FakeAdbServerProvider {
        server?.start()
        return this
    }

    fun restart() {
        // Save current server config and close it
        val config = server?.currentConfig
        server?.close()

        // Build a new server, using saved config to restore as much state as possible
        val builder = FakeAdbServer.Builder()
        config?.let { builder.setConfig(it) }
        server = builder.build()
        server?.start()
    }

    fun createChannelProvider(host: AdbLibHost): TestingChannelProvider {
        return TestingChannelProvider(host, portSupplier = { port })
    }

    override fun close() {
        server?.close()
    }

    fun awaitTermination() {
        server?.awaitServerTermination()
    }

    fun disconnectDevice(deviceSerial: String) {
        server?.disconnectDevice(deviceSerial)
    }

    class TestingChannelProvider(host: AdbLibHost, portSupplier: suspend () -> Int) :
        AdbChannelProvider {

        private val provider = AdbChannelProviderFactory.createOpenLocalHost(host, portSupplier)

        val createdChannels = ArrayList<TestingAdbChannel>()
        val lastCreatedChannel: TestingAdbChannel?
            get() {
                return createdChannels.lastOrNull()
            }

        override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
            val channel = provider.createChannel(timeout, unit)
            return TestingAdbChannel(channel).apply {
                createdChannels.add(this)
            }
        }
    }

    class TestingAdbChannel(private val channel: AdbChannel) : AdbChannel by channel {

        val isOpen: Boolean
            get() = (channel as AdbSocketChannelImpl).isOpen
    }
}
