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
import com.android.adblib.AdbSession
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceList
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.ForwardSocketList
import com.android.adblib.MdnsCheckResult
import com.android.adblib.MdnsServiceList
import com.android.adblib.PairResult
import com.android.adblib.SocketSpec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.takeWhile
import java.io.Closeable

/**
 * The `replay` value of the [MutableSharedFlow] used to simulate the behavior of
 * [AdbHostServices.trackDevices]. We use a value of 1 because the [Flow] returned by
 * [AdbHostServices.trackDevices] should return the current list of devices right away,
 * as ADB Server does.
 */
private const val TRACK_DEVICES_REPLAY = 1

/**
 * The `extraBufferCapacity` value of the [MutableSharedFlow] used to simulate the behavior of
 * [AdbHostServices.trackDevices]. We use a value of `10` to so that call to
 * [MutableSharedFlow.tryEmit] always succeeds in non-suspending functions
 * (e.g. [FakeAdbHostServices.close]). The may need to be increased if new tests require a
 * larger buffer.
 */
private const val TRACK_DEVICES_EXTRA_BUFFER_CAPACITY = 10

/**
 * A fake implementation of [AdbHostServices] for tests.
 */
class FakeAdbHostServices(override val session: AdbSession) : AdbHostServices, Closeable {

    /**
     * The response of the [devices] method.
     */
    var devices = DeviceList(emptyList(), emptyList())
        set(value) {
            field = value
            trackDevicesSharedFlow.emitOrThrow(TrackDeviceElement.Devices(value))
        }

    /**
     * The [MutableSharedFlow] that is used as the upstream [Flow] for implementing
     * [trackDevices].
     *
     * @see TRACK_DEVICES_REPLAY
     * @see TRACK_DEVICES_EXTRA_BUFFER_CAPACITY
     * @see TrackDeviceElement
     */
    private val trackDevicesSharedFlow = MutableSharedFlow<TrackDeviceElement>(
        replay = TRACK_DEVICES_REPLAY,
        extraBufferCapacity = TRACK_DEVICES_EXTRA_BUFFER_CAPACITY
    )
    private val trackDevicesFlow = trackDevicesSharedFlow.asSharedFlow()

    sealed class TrackDeviceElement {
        class Devices(val list: DeviceList) : TrackDeviceElement()
        class Error(val throwable: Throwable) : TrackDeviceElement()
        object Eof : TrackDeviceElement()
    }

    /**
     * Controls the [trackDevices] call by sending a [DeviceList] to the flow (via a channel).
     *
     * @param deviceList the [DeviceList] to send
     */
    suspend fun sendDeviceList(deviceList: DeviceList) {
        trackDevicesSharedFlow.emit(TrackDeviceElement.Devices(deviceList))
    }

    /**
     * Terminates a flow from the [trackDevices] call by closing the backing [Channel]
     *
     * @param e throwable to close with. null means cancellation.
     */
    fun closeTrackDevicesFlow(e: Throwable? = null) {
        if (e == null) {
            trackDevicesSharedFlow.emitOrThrow(TrackDeviceElement.Eof)
        } else {
            trackDevicesSharedFlow.emitOrThrow(TrackDeviceElement.Error(e))
        }
    }

    override fun close() {
        trackDevicesSharedFlow.emitOrThrow(TrackDeviceElement.Eof)
    }

    override suspend fun version(): Int = 0

    override suspend fun devices(format: AdbHostServices.DeviceInfoFormat): DeviceList =
        DeviceList(devices, emptyList())

    override fun trackDevices(format: AdbHostServices.DeviceInfoFormat) = flow {
        trackDevicesFlow
            .takeWhile { it !is TrackDeviceElement.Eof }
            .collect {
                when (it) {
                    is TrackDeviceElement.Devices -> emit(it.list)
                    is TrackDeviceElement.Error -> throw it.throwable
                    is TrackDeviceElement.Eof -> assert(false)
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

    override suspend fun connect(deviceAddress: DeviceAddress) {
        devices = DeviceList(devices + DeviceInfo(serialNumber=deviceAddress.address, deviceState=DeviceState.ONLINE), devices.errors)
    }

    override suspend fun disconnect(deviceAddress: DeviceAddress) {
        devices = DeviceList(devices - DeviceInfo(serialNumber=deviceAddress.address, deviceState=DeviceState.ONLINE), devices.errors)
    }

    private fun <T> MutableSharedFlow<T>.emitOrThrow(value: T) {
        if (!tryEmit(value)) {
            throw IllegalStateException(
                "The shared flow does not have enough extra buffer " +
                        "capacity for this test to run correctly. " +
                        "Increase the 'extraBufferCapacity' value"
            )
        }
    }
}
