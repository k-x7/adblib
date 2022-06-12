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
import com.android.adblib.ConnectedDevice
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ConnectedDeviceImpl(
    override val session: AdbLibSession,
    private val cacheImpl: CoroutineScopeCacheImpl,
    deviceInfo: DeviceInfo
) : ConnectedDevice, AutoCloseable {

    private val deviceInfoStateFlow = MutableStateFlow(deviceInfo)

    override val cache: CoroutineScopeCache
        get() = cacheImpl

    override val deviceInfoFlow = deviceInfoStateFlow.asStateFlow()

    override fun close() {
        // Ensure last state we expose is "offline"
        deviceInfoStateFlow.value =
            deviceInfoStateFlow.value.copy(deviceState = DeviceState.OFFLINE)
        cacheImpl.close()
    }

    fun updateDeviceInfo(deviceInfo: DeviceInfo) {
        deviceInfoStateFlow.value = deviceInfo
    }
}
