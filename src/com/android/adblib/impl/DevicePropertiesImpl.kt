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

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbLibSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceProperties
import com.android.adblib.DeviceProperty
import com.android.adblib.DeviceSelector
import com.android.adblib.shellAsLines
import com.android.adblib.thisLogger
import com.android.adblib.utils.toImmutableMap
import kotlinx.coroutines.flow.toList

class DevicePropertiesImpl(
    val deviceServices: AdbDeviceServices,
    val cache: CoroutineScopeCache,
    val device: DeviceSelector
) : DeviceProperties {

    private val allReadonlyKey = CoroutineScopeCache.Key<Map<String, String>>("allReadonly")

    private val session: AdbLibSession
        get() = deviceServices.session

    override suspend fun all(): List<DeviceProperty> {
        val lines = deviceServices.shellAsLines(device, "getprop").toList()
        return DevicePropertiesParser().parse(lines.asSequence())
    }

    override suspend fun allReadonly(): Map<String, String> {
        return cache.getOrPutSuspending(allReadonlyKey) {
            all()
                .filter { prop -> prop.name.startsWith("ro.") }
                .associate { it.name to it.value }
                .toImmutableMap()
        }
    }

    override suspend fun api(default: Int): Int {
        return try {
            readonlyValue("ro.build.version.sdk").toInt()
        } catch (t: Throwable) {
            thisLogger(this.session).info(t) { "API level could not be determined, returning $default instead" }
            return default
        }
    }

    private suspend fun readonlyValue(name: String): String {
        return allReadonly()[name]
            ?: throw NoSuchElementException("Property '$name' not found in readonly properties")
    }
}
