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

import com.android.adblib.AdbHostServices
import com.android.adblib.AdbLibSession
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
import kotlinx.coroutines.flow.consumeAsFlow
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A fake implementation of [AdbHostServices] for tests.
 */
class FakeAdbHostServices(override val session: AdbLibSession) : AdbHostServices, Closeable {

    /**
     * The response of the [devices] method.
     */
    var devices = DeviceList(emptyList(), emptyList())

    // TODO(aalbert): figure out how to import a GuardedBy annotation.
    //@GuardedBy("itself")
    private var trackDevicesChannels =
        CopyOnWriteArrayList<Channel<DeviceList>>()

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
        return Channel<DeviceList>(UNLIMITED).let {
            synchronized(trackDevicesChannels) {
                trackDevicesChannels.add(it)
            }
            it.consumeAsFlow()
        }
    }

    override suspend fun hostFeatures(): List<String> {
        TODO("Not yet implemented")
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

    override suspend fun getSerialNo(device: DeviceSelector): String {
        TODO("Not yet implemented")
    }

    override suspend fun getDevPath(device: DeviceSelector): String {
        TODO("Not yet implemented")
    }

    override suspend fun features(device: DeviceSelector): List<String> {
        TODO("Not yet implemented")
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

