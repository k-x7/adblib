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

import com.android.adblib.AdbHostServices
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceList
import com.android.adblib.ErrorLine
import com.android.adblib.TrackedDeviceList
import com.android.adblib.thisLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import java.io.EOFException
import java.time.Duration

internal class SessionDeviceTracker(val session: AdbLibSession) {

    private val logger = thisLogger(session.host)

    fun createStateFlow(retryDelay: Duration): StateFlow<TrackedDeviceList> {
        var connectionId = 0
        return session.hostServices.trackDevices(AdbHostServices.DeviceInfoFormat.LONG_FORMAT)
            .onStart {
                connectionId++
                logger.debug { "trackDevices() is starting, connection id=$connectionId" }
            }
            .map { deviceList -> TrackedDeviceList(connectionId, deviceList, null) }
            .retryWithDelay(retryDelay) { throwable ->
                connectionId++
                if (throwable is EOFException) {
                    logger.info { "trackDevices() reached EOF, will retry in ${retryDelay.toMillis()} millis, connection id=$connectionId" }
                } else {
                    logger.info(throwable) { "trackDevices() failed, will retry in ${retryDelay.toMillis()} millis, connection id=$connectionId" }
                }
                TrackedDeviceList(connectionId, TrackerDisconnected.instance, throwable)
            }.stateIn(
                session.scope,
                SharingStarted.Lazily,
                TrackedDeviceList(connectionId, TrackerConnecting.instance, null)
            )
    }
}

private fun <T> Flow<T>.retryWithDelay(
    retryDelay: Duration,
    retryValue: (Throwable) -> T?
): Flow<T> {
    return retryWhen { throwable, _ ->
        if (throwable is CancellationException) {
            // Let cancellation of upstream flow propagate
            false
        } else {
            retryValue(throwable)?.let { emit(it) }
            delay(retryDelay.toMillis())
            true
        }
    }
}

internal object TrackerDisconnected {

    private val error = ErrorLine("Device tracking session has been disconnected from ADB", 0, "")
    val instance = DeviceList(emptyList(), listOf(error))
}

internal object TrackerConnecting {

    private val error = ErrorLine("Device tracking session has not started yet", 0, "")
    val instance = DeviceList(emptyList(), listOf(error))
}
