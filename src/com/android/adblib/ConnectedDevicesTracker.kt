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

/**
 * Allow accessing a [CoroutineScopeCache] per connected device.
 * The [CoroutineScopeCache] entries of a given device are cleared when the
 * device is disconnected.
 */
interface ConnectedDevicesTracker {

    /**
     * The scope used to track connected devices. This [ConnectedDevicesTracker] instance
     * stops working when this [scope] is cancelled.
     */
    val scope: CoroutineScope

    /**
     * Returns the [CoroutineScopeCache] associated to the connected device with the
     * given [serialNumber].
     *
     * If a device is disconnected and reconnected again, a new empty cache is returned
     * and the previous cache is emptied.
     *
     * If the device is not connected, a "no-op" cache is returned.
     */
    fun deviceCache(serialNumber: String): CoroutineScopeCache

    /**
     * Returns the [CoroutineScopeCache] associated to the connected device
     * corresponding to the given [DeviceSelector]
     *
     * If a device is disconnected and reconnected again, a new empty cache is returned.
     *
     * If the device is not connected, a "no-op" cache is returned.
     */
    suspend fun deviceCache(selector: DeviceSelector): CoroutineScopeCache
}
