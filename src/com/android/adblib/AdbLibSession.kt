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

import com.android.adblib.AdbLibSession.Companion.create
import com.android.adblib.impl.AdbLibSessionImpl
import com.android.adblib.impl.SessionDeviceTracker
import com.android.adblib.impl.TrackerConnecting
import com.android.adblib.impl.TrackerDisconnected
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
     *
     * @throws ClosedSessionException if this [AdbLibSession] has been [closed][close].
     */
    val host: AdbLibHost

    /**
     * An [AdbChannelFactory] that can be used to create various implementations of
     * [AdbChannel], [AdbInputChannel] and [AdbOutputChannel] for files, streams, etc.
     *
     * @throws ClosedSessionException if this [AdbLibSession] has been [closed][close].
     */
    val channelFactory: AdbChannelFactory

    /**
     * An [AdbHostServices] implementation for this session.
     *
     * @throws ClosedSessionException if this [AdbLibSession] has been [closed][close].
     */
    val hostServices: AdbHostServices

    /**
     * An [AdbDeviceServices] implementation for this session.
     *
     * @throws ClosedSessionException if this [AdbLibSession] has been [closed][close].
     */
    val deviceServices: AdbDeviceServices

    /**
     * [CoroutineScope] that remains active until the session is [closed][close].
     *
     * Useful when creating coroutines, e.g. with [CoroutineScope.launch], that need to be
     * automatically cancelled when the session is [closed][close].
     *
     * @throws ClosedSessionException if this [AdbLibSession] has been [closed][close].
     */
    val scope: CoroutineScope

    /**
     * Thread safe in-memory cache to store objects for as long this [AdbLibSession] is active.
     * Any value added to the cache that implements [AutoCloseable] is
     * [closed][AutoCloseable.close] when this [AdbLibSession] is closed.
     *
     * @throws ClosedSessionException if this [AdbLibSession] has been [closed][close].
     */
    val cache: SessionCache

    /**
     * Throws [ClosedSessionException] if this [AdbLibSession] has been [closed][close].
     */
    fun throwIfClosed()

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

/**
 * Exception thrown when accessing services of an [AdbLibSession] that has been closed.
 */
class ClosedSessionException(message: String) : IllegalStateException(message)

/**
 * Returns a [StateFlow] that emits a new [TrackedDeviceList] everytime a device state change
 * is detected by the ADB Host.
 *
 * The implementation uses a [stateIn] operator with the following characteristics:
 *
 * * The initial value of the returned [StateFlow] is always an empty [TrackedDeviceList]
 *   of type [isTrackerConnecting]. This is because it may take some time to collect
 *   the initial list of devices.
 *
 * * The upstream flow is a [AdbHostServices.trackDevices] that is retried
 *   after a delay of [retryDelay] in case of error.
 *
 *  * In case of error in the upstream flow, an empty [TrackedDeviceList] (of type
 *   [isTrackerDisconnected]) is emitted to downstream flows and the
 *   [TrackedDeviceList.connectionId] is incremented.
 *
 * * The returned [StateFlow] is unique to this [AdbLibSession], meaning all collectors of
 *   the returned flow share a single underlying [AdbHostServices.trackDevices] connection.
 *
 * * The returned [StateFlow] activates a [AdbHostServices.trackDevices] connection only when
 *   there are active downstream flows, see [SharingStarted.WhileSubscribed].
 *
 * * The returned [StateFlow] runs in a separate coroutine in the [AdbLibSession.scope].
 *   If the scope is cancelled, the [StateFlow] stops emitting new values, but downstream
 *   flows are not terminated. It is up to the caller to use an appropriate [CoroutineScope]
 *   when collecting the returned flow. A typical usage would be to use the
 *   [AdbLibSession.scope] when collecting, for example:
 *
 *   ```
 *     val session: AdbLibSession
 *     session.scope.launch {
 *         session.trackDevices.flowOn(Dispatchers.Default).collect {
 *             // Collect until session scope is cancelled.
 *         }
 *     }
 *   ```
 *
 * * Downstream flows may not be able to collect all values produced by the [StateFlow],
 *   because [StateFlow] use the [BufferOverflow.DROP_OLDEST] strategy. Downstream flows
 *   should keep track of [TrackedDeviceList.connectionId] to determine if
 *   [DeviceInfo.serialNumber] values can be compared against previously collected
 *   serial numbers.
 *
 * * Because the [StateFlow] use the [BufferOverflow.DROP_OLDEST] strategy, slow downstream
 *   flows don't prevent other (more efficient) downstream flows from collecting new
 *   values when available.
 *
 * **Note**: [DeviceInfo] entries of the [DeviceList] are always of the
 * [long format][AdbHostServices.DeviceInfoFormat.LONG_FORMAT].
 */
fun AdbLibSession.trackDevices(
    retryDelay: Duration = Duration.ofSeconds(2)
): StateFlow<TrackedDeviceList> {
    data class MyKey(val duration: Duration) :
        SessionCache.Key<StateFlow<TrackedDeviceList>>("trackDevices")

    return this.cache.getOrPut(MyKey(retryDelay)) {
        SessionDeviceTracker(this).createStateFlow(retryDelay)
    }
}

/**
 * A [list][DeviceList] of [DeviceInfo] as collected by the [state flow][StateFlow]
 * returned by [AdbLibSession.trackDevices].
 */
class TrackedDeviceList(
    /**
     * Connection id, unique to each underlying [AdbHostServices.trackDevices] connection.
     * [DeviceInfo.serialNumber] may not uniquely identify a device if their connection ids
     * are not the same.
     */
    val connectionId: Int,
    /**
     * The [list][DeviceList] of [DeviceInfo], the list is always empty if the underlying
     * connection to ADB failed.
     */
    val devices: DeviceList,
    /**
     * The last [Throwable] collected when the underlying ADB connection failed, or `null`
     * if the underlying ADB connection is active.
     */
    val throwable: Throwable?
) {

    override fun toString(): String {
        return "${this::class.simpleName}: connectionId=$connectionId, " +
                "device count=${devices.size}, throwable=$throwable"
    }
}

/**
 * Returns `true` if this [TrackedDeviceList] instance has been produced by
 * [AdbLibSession.trackDevices] due to a connection failure.
 */
val TrackedDeviceList.isTrackerDisconnected: Boolean
    get() {
        return this.devices === TrackerDisconnected.instance
    }

/**
 * Returns `true` if this [TrackedDeviceList] instance is the initial value produced by
 * the [StateFlow] returned by [AdbLibSession.trackDevices].
 */
val TrackedDeviceList.isTrackerConnecting: Boolean
    get() {
        return this.devices === TrackerConnecting.instance
    }
