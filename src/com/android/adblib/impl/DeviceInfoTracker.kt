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

import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceSelector
import com.android.adblib.isTrackerConnecting
import com.android.adblib.isTrackerDisconnected
import com.android.adblib.thisLogger
import com.android.adblib.trackDeviceInfo
import com.android.adblib.trackDevices
import com.android.adblib.utils.SuspendingLazy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.transformWhile

/**
 * Implementation of [AdbLibSession.trackDeviceInfo]
 */
internal class DeviceInfoTracker(
    private val session: AdbLibSession,
    private val device: DeviceSelector
) {

    private val logger = thisLogger(session.host)

    private val deviceSerialNumber = SuspendingLazy {
        try {
            session.hostServices.getSerialNo(device)
        } catch (e: AdbFailResponseException) {
            logger.info(e) { "Device '$device' not found, ending tracking" }
            // We return the empty string, which is a serial number that won't
            // match any device, so tracking will end failing to find a matching device.
            ""
        }
    }

    private var connectionId: Int? = null

    fun createFlow(): Flow<DeviceInfo> {
        // Note: 'transformWhile' is not experimental anymore
        // See https://github.com/Kotlin/kotlinx.coroutines/commit/8d1ee7d3230a66f7c26910c1b17746fd3ada57d8
        @OptIn(ExperimentalCoroutinesApi::class)
        return session.trackDevices().transformWhile { trackedDeviceList ->
            when {
                trackedDeviceList.isTrackerConnecting -> {
                    // Keep the flow going, as we don't have a valid list yet
                    true
                }
                trackedDeviceList.isTrackerDisconnected -> {
                    // Stop the flow, since underlying ADB connection has ended
                    logger.info { "Device tracking disconnected, ending tracking of device '$device'" }
                    false
                }
                else -> {
                    // Ensure we don't try to match serial numbers across distinct
                    // ADB connections.
                    if (connectionId == null) {
                        connectionId = trackedDeviceList.connectionId
                        logger.debug { "Device tracking for '$device' uses connection ID '$connectionId'" }
                    }

                    if (connectionId != trackedDeviceList.connectionId) {
                        // Stop the flow, since underlying ADB connection has changed
                        logger.info { "Device tracking connection ID changed, ending tracking of device '$device'" }
                        false
                    } else {
                        // Emit device and keep the flow going as long as the device is in the list
                        trackedDeviceList.devices.find {
                            it.serialNumber == deviceSerialNumber.value()
                        }?.let {
                            emit(it)
                            true
                        } ?: false
                    }
                }
            }
        }.flowOn(session.host.ioDispatcher)
    }
}
