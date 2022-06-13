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
package com.android.adblib.impl

import com.android.adblib.AdbLibSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.ConnectedDevicesTracker
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import com.android.adblib.trackDevices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

internal class ConnectedDevicesTrackerImpl(private val session: AdbLibSession) : ConnectedDevicesTracker {

    private val logger = thisLogger(session)

    private val deviceMapLock = Any()
    //TODO: Add annotation when/if this library has the corresponding dependency
    //@GuardedBy("deviceMapLock")
    private val deviceMap = HashMap<String, CoroutineScopeCacheImpl>()

    private val monitorJob: Job by lazy {
        launchDeviceTracking()
    }

    internal val deviceCount: Int
        get() {
            synchronized(deviceMapLock) {
                return deviceMap.size
            }
        }

    fun start() {
        // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
        monitorJob
    }

    override val scope: CoroutineScope
        get() = session.scope

    override fun deviceCache(serialNumber: String): CoroutineScopeCache {
        return synchronized(deviceMapLock) {
            deviceMap[serialNumber] ?: inactiveCoroutineScopeCache
        }
    }

    override suspend fun deviceCache(selector: DeviceSelector): CoroutineScopeCache {
        return kotlin.runCatching {
            val serialNumber = session.hostServices.getSerialNo(selector)
            deviceCache(serialNumber)
        }.getOrDefault(inactiveCoroutineScopeCache)
    }

    private fun launchDeviceTracking(): Job {
        logger.debug { "Starting device tracking coroutine" }
        var connectionId: Int? = null
        return scope.launch {
            session.trackDevices().collect { trackedDeviceList ->
                if (connectionId != trackedDeviceList.connectionId) {
                    // When we have a new connection ID, we clean up everything
                    connectionId = trackedDeviceList.connectionId
                    updateCache(emptySet())
                }

                val effectiveDevices =
                    trackedDeviceList
                        .devices
                        .entries
                        .map { deviceInfo ->
                            deviceInfo.serialNumber
                        }.toHashSet()
                updateCache(effectiveDevices)
            }
        }
    }

    private fun updateCache(activeSerialNumbers: Set<String>) {
        val toClose = mutableListOf<CoroutineScopeCacheImpl>()
        synchronized(deviceMapLock) {
            val cachedSerialNumbers = deviceMap.keys.toHashSet()
            val added = activeSerialNumbers - cachedSerialNumbers
            val removed = cachedSerialNumbers - activeSerialNumbers

            added.forEach { serial ->
                logger.debug { "Adding device cache for device $serial" }
                deviceMap[serial] = CoroutineScopeCacheImpl(scope)
            }
            removed.forEach { serial ->
                logger.debug { "Removing device cache for device $serial" }
                deviceMap.remove(serial)?.also { toClose.add(it) }
            }
        }
        // Close instances outside of lock to prevent potential deadlocks
        toClose.forEach {
            logger.debug { "Closing device cache for device $it" }
            it.close()
        }
    }

    private val inactiveCoroutineScopeCache = object : CoroutineScopeCache {
        val job: Job = SupervisorJob().also {
            it.cancel(CancellationException("This device cache is inactive"))
        }

        override val scope = CoroutineScope(job)

        override fun <T> getOrPut(key: CoroutineScopeCache.Key<T>, defaultValue: () -> T): T {
            return defaultValue()
        }

        override suspend fun <T> getOrPutSuspending(
            key: CoroutineScopeCache.Key<T>,
            defaultValue: suspend CoroutineScope.() -> T
        ): T {
            return scope.defaultValue()
        }

        override fun <T> getOrPutSuspending(
            key: CoroutineScopeCache.Key<T>,
            fastDefaultValue: () -> T,
            defaultValue: suspend CoroutineScope.() -> T
        ): T {
            return fastDefaultValue()
        }
    }
}
