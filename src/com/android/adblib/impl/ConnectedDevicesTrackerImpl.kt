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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.ConnectedDevicesTracker
import com.android.adblib.DeviceInfo
import com.android.adblib.thisLogger
import com.android.adblib.trackDevices
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal class ConnectedDevicesTrackerImpl(override val session: AdbSession) :
    ConnectedDevicesTracker {

    private val logger = thisLogger(session)

    private val connectedDevicesStateFlow = MutableStateFlow(emptyList<ConnectedDevice>())

    override val connectedDevices = connectedDevicesStateFlow.asStateFlow()

    private val deviceMapLock = Any()

    //TODO: Add annotation when/if this library has the corresponding dependency
    //@GuardedBy("deviceMapLock")
    private val deviceMap = HashMap<String, ConnectedDeviceImpl>()

    private val monitorJob: Job by lazy {
        launchDeviceTracking()
    }

    fun start() {
        // Note: We rely on "lazy" to ensure the tracking coroutine is launched only once
        monitorJob
    }

    private fun launchDeviceTracking(): Job {
        return session.scope.launch {
            logger.debug { "Starting connected devices tracker coroutine" }
            try {
                var connectionId: Int? = null
                session.trackDevices().collect { trackedDeviceList ->
                    if (connectionId != trackedDeviceList.connectionId) {
                        // When we have a new connection ID, we clean up everything
                        connectionId = trackedDeviceList.connectionId
                        updateCache(emptyMap())
                    }

                    updateCache(trackedDeviceList.devices.associateBy { it.serialNumber })
                }
            } finally {
                logger.debug { "Shutting down connected devices tracker coroutine" }
                connectedDevicesStateFlow.value = emptyList()
            }
        }
    }

    private fun updateCache(activeDevices: Map<String, DeviceInfo>) {
        val toClose = mutableListOf<ConnectedDeviceImpl>()
        val toUpdate = mutableListOf<Pair<ConnectedDeviceImpl, DeviceInfo>>()
        val connectedDeviceList = synchronized(deviceMapLock) {
            val activeSerialNumbers = activeDevices.keys
            val cachedSerialNumbers = deviceMap.keys
            val added = activeSerialNumbers - cachedSerialNumbers
            val removed = cachedSerialNumbers - activeSerialNumbers
            val updated = cachedSerialNumbers intersect activeSerialNumbers

            added.forEach { serial ->
                logger.debug { "Adding connected device for device $serial" }
                deviceMap[serial] =
                    ConnectedDeviceImpl(
                        session,
                        CoroutineScopeCacheImpl(session.scope),
                        activeDevices.getValue(serial)
                    )
            }
            removed.forEach { serial ->
                logger.debug { "Removing connected device for device $serial" }
                deviceMap.remove(serial)?.also { toClose.add(it) }
            }
            updated.forEach { serial ->
                toUpdate.add(Pair(deviceMap.getValue(serial), activeDevices.getValue(serial)))
            }
            deviceMap.values.toList()
        }
        // Close instances outside of lock to prevent potential deadlocks
        toClose.forEach {
            logger.debug { "Closing connected device for device $it" }
            it.close()
        }
        // Update instances outside of lock to prevent potential deadlocks
        toUpdate.forEach { (device, deviceInfo) ->
            logger.debug { "Updating connected device info flow for device ${deviceInfo.serialNumber}" }
            device.updateDeviceInfo(deviceInfo)
        }

        // Final step: Update the flow of connected devices
        logger.debug { "Updating connected devices flow to ${connectedDeviceList.size} devices" }
        connectedDevicesStateFlow.value = connectedDeviceList
    }
}
