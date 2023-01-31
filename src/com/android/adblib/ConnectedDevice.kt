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
package com.android.adblib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import java.time.Duration

/**
 * Abstraction over a device currently connected to ADB. An instance of [ConnectedDevice] is
 * valid as long as the device is connected to the underlying ADB server, and becomes
 * invalid as soon as the device is disconnected or ADB server is restarted.
 *
 * @see [AdbSession.connectedDevicesTracker]
 */
interface ConnectedDevice {

    /**
     * The [session][AdbSession] this device belongs to. When the session is
     * [closed][AdbSession.close], this [ConnectedDevice] instance becomes invalid.
     */
    val session: AdbSession

    /**
     * Returns a [CoroutineScopeCache] associated to this [ConnectedDevice]. The cache
     * is cleared when the device is disconnected.
     */
    val cache: CoroutineScopeCache

    /**
     * The [StateFlow] of [DeviceInfo] corresponding to change of state of the device.
     * Once the device is disconnected, the [DeviceInfo.deviceState] is always
     * set to [DeviceState.OFFLINE].
     */
    val deviceInfoFlow: StateFlow<DeviceInfo>
}

/**
 * A [CoroutineScope] tied to this [ConnectedDevice] instance. The scope is cancelled
 * when the device is disconnected, when the [ConnectedDevice.session] is closed or when
 * the ADB server is restarted.
 */
val ConnectedDevice.scope: CoroutineScope
    get() = cache.scope

/**
 * The "serial number" of this [device][ConnectedDevice], used to identify a device with
 * the ADB server as long as the device is connected.
 */
val ConnectedDevice.serialNumber: String
    get() = deviceInfoFlow.value.serialNumber

/**
 * The [DeviceSelector] of this [device][ConnectedDevice], used to identify a device with
 * the ADB server as long as the device is connected.
 */
val ConnectedDevice.selector: DeviceSelector
    get() = DeviceSelector.fromSerialNumber(serialNumber)

/**
 * Whether the device is [DeviceState.ONLINE], i.e. ready to be used.
 */
val ConnectedDevice.isOnline: Boolean
    get() = deviceInfoFlow.value.deviceState == DeviceState.ONLINE

/**
 * The current (or last known) [DeviceInfo] for this [ConnectedDevice].
 */
val ConnectedDevice.deviceInfo: DeviceInfo
    get() = deviceInfoFlow.value

/**
 * Shortcut to the [DeviceProperties] of this device.
 */
suspend fun ConnectedDevice.deviceProperties(): DeviceProperties =
    session.deviceServices.deviceProperties(DeviceSelector.fromSerialNumber(serialNumber))

/**
 * When the device comes online, starts and returns the flow from [transform].
 * Retries the flow if an exception occurs and the device is still connected.
 */
fun <R> ConnectedDevice.flowWhenOnline(
    retryDelay: Duration,
    transform: suspend (device: ConnectedDevice) -> Flow<R>
): Flow<R> {
    val device = this
    return deviceInfoFlow
        .map { it.deviceState }
        .filter { it == DeviceState.ONLINE }
        .distinctUntilChanged()
        .flatMapConcat { transform(device) }
        .retryWhen { throwable, _ ->
            device.thisLogger(session).warn(
                throwable,
                "Device $device flow failed with error '${throwable.message}', " +
                        "retrying in ${retryDelay.seconds} sec"
            )
            // We retry as long as the device is valid
            if (device.scope.isActive) {
                delay(retryDelay.toMillis())
            }
            device.scope.isActive
        }.flowOn(session.host.ioDispatcher)
}
