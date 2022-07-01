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

import com.android.adblib.AdbSession.Companion.create
import com.android.adblib.CoroutineScopeCache.Key
import com.android.adblib.impl.AdbSessionImpl
import com.android.adblib.impl.ConnectedDevicesTrackerImpl
import com.android.adblib.impl.DeviceInfoTracker
import com.android.adblib.impl.SessionDeviceTracker
import com.android.adblib.impl.TrackerConnecting
import com.android.adblib.impl.TrackerDisconnected
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.time.Duration
import kotlin.coroutines.CoroutineContext

/**
 * Provides access to various ADB services (e.g. [AdbHostServices], [AdbDeviceServices]) for
 * a given [AdbLibHost]. The [close] method should be called when the session is not needed
 * anymore, to ensure all pending operations and all optional state are released.
 *
 * This is the main entry point of `adblib`, use the [create] method to create an instance.
 */
interface AdbSession : AutoCloseable {

    /**
     * The [AdbLibHost] implementation provided by the hosting application for environment
     * specific configuration.
     *
     * @throws ClosedSessionException if this [AdbSession] has been [closed][close].
     */
    val host: AdbLibHost

    /**
     * An [AdbChannelFactory] that can be used to create various implementations of
     * [AdbChannel], [AdbInputChannel] and [AdbOutputChannel] for files, streams, etc.
     *
     * @throws ClosedSessionException if this [AdbSession] has been [closed][close].
     */
    val channelFactory: AdbChannelFactory

    /**
     * An [AdbHostServices] implementation for this session.
     *
     * @throws ClosedSessionException if this [AdbSession] has been [closed][close].
     */
    val hostServices: AdbHostServices

    /**
     * An [AdbDeviceServices] implementation for this session.
     *
     * @throws ClosedSessionException if this [AdbSession] has been [closed][close].
     */
    val deviceServices: AdbDeviceServices

    /**
     * [CoroutineScope] that remains active until the session is [closed][close].
     *
     * Useful when creating coroutines, e.g. with [CoroutineScope.launch], that need to be
     * automatically cancelled when the session is [closed][close].
     *
     * @throws ClosedSessionException if this [AdbSession] has been [closed][close].
     */
    val scope: CoroutineScope

    /**
     * Thread safe in-memory cache to store objects for as long this [AdbSession] is active.
     * Any value added to the cache that implements [AutoCloseable] is
     * [closed][AutoCloseable.close] when this [AdbSession] is closed.
     *
     * @throws ClosedSessionException if this [AdbSession] has been [closed][close].
     */
    val cache: CoroutineScopeCache

    /**
     * Throws [ClosedSessionException] if this [AdbSession] has been [closed][close].
     */
    fun throwIfClosed()

    companion object {

        /**
         * Creates an instance of an [AdbSession] given an [AdbLibHost] instance.
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
        ): AdbSession {
            return AdbSessionImpl(host, channelProvider, connectionTimeout.toMillis())
        }
    }
}

/**
 * Exception thrown when accessing services of an [AdbSession] that has been closed.
 */
class ClosedSessionException(message: String) : CancellationException(message)

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
 * * The returned [StateFlow] is unique to this [AdbSession], meaning all collectors of
 *   the returned flow share a single underlying [AdbHostServices.trackDevices] connection.
 *
 * * The returned [StateFlow] activates a [AdbHostServices.trackDevices] connection only when
 *   there are active downstream flows, see [SharingStarted.WhileSubscribed].
 *
 * * The returned [StateFlow] runs in a separate coroutine in the [AdbSession.scope].
 *   If the scope is cancelled, the [StateFlow] stops emitting new values, but downstream
 *   flows are not terminated. It is up to the caller to use an appropriate [CoroutineScope]
 *   when collecting the returned flow. A typical usage would be to use the
 *   [AdbSession.scope] when collecting, for example:
 *
 *   ```
 *     val session: AdbSession
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
fun AdbSession.trackDevices(
    retryDelay: Duration = Duration.ofSeconds(2)
): StateFlow<TrackedDeviceList> {
    data class MyKey(val duration: Duration) : Key<SessionDeviceTracker>("trackDevices")

    // Note: We return the `stateFlow` outside the cache `getOrPut` method, since
    // `getOrPut` may create multiple instances in case
    // of concurrent first cache hit.
    return this.cache.getOrPut(MyKey(retryDelay)) {
        SessionDeviceTracker(this, retryDelay)
    }.stateFlow
}

/**
 * A [list][DeviceList] of [DeviceInfo] as collected by the [state flow][StateFlow]
 * returned by [AdbSession.trackDevices].
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
 * [AdbSession.trackDevices] due to a connection failure.
 */
val TrackedDeviceList.isTrackerDisconnected: Boolean
    get() {
        return this.devices === TrackerDisconnected.instance
    }

/**
 * Returns `true` if this [TrackedDeviceList] instance is the initial value produced by
 * the [StateFlow] returned by [AdbSession.trackDevices].
 */
val TrackedDeviceList.isTrackerConnecting: Boolean
    get() {
        return this.devices === TrackerConnecting.instance
    }

/**
 * Returns a flow of [DeviceInfo] that tracks changes to a given [device][DeviceSelector],
 * typically changes to the [DeviceInfo.deviceState] value.
 *
 * The flow terminates when the device is no longer connected or when the ADB
 * connection is terminated.
 */
fun AdbSession.trackDeviceInfo(device: DeviceSelector): Flow<DeviceInfo> {
    return DeviceInfoTracker(this, device).createFlow()
}

/**
 * Returns a [CoroutineScope] that can be used to run coroutines that should be cancelled
 * when the given [device] is disconnected (or ADB connection is terminated).
 *
 * The returned scope is also cancelled when the [AdbSession] is [closed][AdbSession.close].
 *
 * The returned [CoroutineScope] uses a [CoroutineContext] with a [SupervisorJob] tied to
 * the [device] lifecycle and a [AdbLibHost.ioDispatcher].
 *
 * @see [trackDeviceInfo]
 */
fun AdbSession.createDeviceScope(device: DeviceSelector): CoroutineScope {
    val session = this
    val parentJob = session.scope.coroutineContext.job
    return CoroutineScope(SupervisorJob(parentJob) + session.host.ioDispatcher).also { deviceScope ->
        // Launch a coroutine that track the device state and cancel the scope
        // when that device disconnects.
        deviceScope.launch {
            try {
                // Use device info tracking flow
                session.trackDeviceInfo(device).collect()
            } finally {
                val msg = "Device $device has been disconnected, cancelling job"
                thisLogger(session.host).debug { msg }
                deviceScope.cancel(CancellationException(msg))
            }
        }
    }
}

/**
 * Returns the [ConnectedDevicesTracker] associated to this session
 */
val AdbSession.connectedDevicesTracker: ConnectedDevicesTracker
    get() {
        return this.cache.getOrPut(ConnectedDevicesManagerKey) {
            ConnectedDevicesTrackerImpl(this)
        }.also {
            // Note: We do this outside of the cache lookup to ensure `start`
            // is called only on the instance stored in the cache.
            (it as ConnectedDevicesTrackerImpl).start()
        }
    }

/**
 * The [Key] used to identify the [ConnectedDevicesTracker] in [AdbSession.cache].
 */
private object ConnectedDevicesManagerKey : Key<ConnectedDevicesTracker>(ConnectedDevicesTracker::class.java.simpleName)

/**
 * Returns a [CoroutineScopeCache] that keeps entries alive until the device corresponding
 * to [serialNumber] is disconnected.
 */
fun AdbSession.deviceCache(serialNumber: String): CoroutineScopeCache {
    return connectedDevicesTracker.deviceCache(serialNumber)
}

/**
 * Returns a [CoroutineScopeCache] that keeps entries alive until the device corresponding
 * to [selector] is disconnected.
 */
suspend fun AdbSession.deviceCache(selector: DeviceSelector): CoroutineScopeCache {
    return connectedDevicesTracker.deviceCache(selector)
}
