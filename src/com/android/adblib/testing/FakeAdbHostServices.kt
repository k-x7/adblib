/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adblib.testing

import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbSession
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceList
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.ForwardSocketList
import com.android.adblib.MdnsCheckResult
import com.android.adblib.MdnsServiceList
import com.android.adblib.PairResult
import com.android.adblib.SocketSpec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.yield
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A fake implementation of [AdbHostServices] for tests.
 */
class FakeAdbHostServices(override val session: AdbSession) : AdbHostServices, Closeable {

    /**
     * The response of the [devices] method.
     */
    var devices = DeviceList(emptyList(), emptyList())

    // TODO(aalbert): figure out how to import a GuardedBy annotation.
    //@GuardedBy("itself")
    private var trackDevicesChannels =
        CopyOnWriteArrayList<Channel<DeviceList>>()

    suspend fun awaitConnection() {
        while (trackDevicesChannels.filter { !it.isClosedForSend }.isEmpty()) {
            yield()
        }
    }

    /**
     * Controls the [trackDevices] call by sending a [DeviceList] to the flow (via a channel).
     *
     * @param deviceList the [DeviceList] to send
     * @param index indicates which channel to send it to. A new channel is created every time
     * [trackDevices] is called. -1 means the latest.
     */
    suspend fun sendDeviceList(deviceList: DeviceList, index: Int = -1) {
        val channel = synchronized(trackDevicesChannels) {
            try {
                if (index >= 0) trackDevicesChannels[index] else trackDevicesChannels.last()
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalStateException("Channel not found. Did you call trackDevices()?")
            }
        }
        channel.send(deviceList)
    }

    /**
     * Terminates a flow from the [trackDevices] call by closing the backing [Channel]
     *
     * @param index indicates which channel to colse it to. A new channel is created every time
     * [trackDevices] is called. -1 means the latest.
     * @param e throwable to close with. null means cancellation.
     */
    fun closeTrackDevicesFlow(index: Int = -1, e: Throwable? = null) {
        val channel = synchronized(trackDevicesChannels) {
            try {
                if (index >= 0) trackDevicesChannels[index] else trackDevicesChannels.last()
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalStateException("Channel not found. Did you call trackDevices()?")
            }
        }
        channel.close(e)
    }

    override fun close() {
        synchronized(trackDevicesChannels) {
            trackDevicesChannels.forEach { it.close() }
            trackDevicesChannels.clear()
        }
    }

    override suspend fun version(): Int = 0

    override suspend fun devices(format: AdbHostServices.DeviceInfoFormat): DeviceList =
        DeviceList(devices, emptyList())

    override fun trackDevices(format: AdbHostServices.DeviceInfoFormat): Flow<DeviceList> {
        return flow {
            val devices = this@FakeAdbHostServices.devices
            val channel = Channel<DeviceList>(UNLIMITED).apply {
                synchronized(trackDevicesChannels) {
                    trackDevicesChannels.add(this)
                }
            }

            this.emit(devices)
            channel.receiveAsFlow().collect {
                this.emit(it)
            }
        }
    }

    override suspend fun hostFeatures(): List<String> {
        return listOf(
            "shell_v2",
            "cmd",
            "stat_v2",
            "ls_v2",
            "fixed_push_mkdir",
            "apex",
            "abb",
            "fixed_push_symlink_timestamp",
            "abb_exec",
            "remount_shell",
            "track_app",
            "sendrecv_v2",
            "sendrecv_v2_brotli",
            "sendrecv_v2_lz4",
            "sendrecv_v2_zstd",
            "sendrecv_v2_dry_run_send",
            "openscreen_mdns",
            "push_sync"
        )
    }

    override suspend fun kill() {
        TODO("Not yet implemented")
    }

    override suspend fun mdnsCheck(): MdnsCheckResult {
        TODO("Not yet implemented")
    }

    override suspend fun mdnsServices(): MdnsServiceList {
        TODO("Not yet implemented")
    }

    override suspend fun pair(deviceAddress: DeviceAddress, pairingCode: String): PairResult {
        TODO("Not yet implemented")
    }

    override suspend fun getState(device: DeviceSelector): DeviceState {
        TODO("Not yet implemented")
    }

    override suspend fun getSerialNo(device: DeviceSelector, forceRoundTrip: Boolean): String {
        return device.serialNumber ?:
            TODO("Not yet implemented: Unsupported device selector format")
    }

    override suspend fun getDevPath(device: DeviceSelector): String {
        TODO("Not yet implemented")
    }

    override suspend fun features(device: DeviceSelector): List<String> {
        return listOf(
            "sendrecv_v2_brotli",
            "remount_shell",
            "sendrecv_v2",
            "abb_exec",
            "fixed_push_mkdir",
            "fixed_push_symlink_timestamp",
            "abb",
            "shell_v2",
            "cmd",
            "ls_v2",
            "apex",
            "stat_v2",
        )
    }

    override suspend fun listForward(): ForwardSocketList {
        TODO("Not yet implemented")
    }

    override suspend fun forward(
        device: DeviceSelector,
        local: SocketSpec,
        remote: SocketSpec,
        rebind: Boolean
    ): String? {
        TODO("Not yet implemented")
    }

    override suspend fun killForward(device: DeviceSelector, local: SocketSpec) {
        TODO("Not yet implemented")
    }

    override suspend fun killForwardAll(device: DeviceSelector) {
        TODO("Not yet implemented")
    }
}

