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
import com.android.adblib.AdbFeatures
import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.DeviceProperties
import com.android.adblib.DeviceProperty
import com.android.adblib.DevicePropertyNames.RO_BUILD_VERSION_SDK
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.availableFeatures
import com.android.adblib.thisLogger
import com.android.adblib.utils.LineShellCollector
import com.android.adblib.utils.LineShellV2Collector
import com.android.adblib.utils.TextShellCollector
import com.android.adblib.utils.rethrowCancellation
import com.android.adblib.utils.toImmutableMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList

class DevicePropertiesImpl(
    val deviceServices: AdbDeviceServices,
    val cache: CoroutineScopeCache,
    val device: DeviceSelector
) : DeviceProperties {

    private val logger = thisLogger(deviceServices.session)

    private val allReadonlyKey = CoroutineScopeCache.Key<Map<String, String>>("allReadonly")

    private val session: AdbSession
        get() = deviceServices.session

    override suspend fun all(): List<DeviceProperty> {
        val shellV2Supported = runCatching {
            session.hostServices.availableFeatures(device).contains(AdbFeatures.SHELL_V2)
        }.getOrElse {
            it.rethrowCancellation()
            // Very old devices (and ADB servers) don't support the "features" service
            logger.info { "Error obtaining device features: $it" }
            false
        }

        return if (shellV2Supported) {
            // Use "shell,v2" if available
            val lines = deviceServices.shellV2(device, "getprop", LineShellV2Collector())
                .mapNotNull {
                    when(it) {
                        is ShellCommandOutputElement.StdoutLine -> it.contents
                        is ShellCommandOutputElement.ExitCode -> null
                        is ShellCommandOutputElement.StderrLine -> null
                    }
                }.toList()
            DevicePropertiesParser().parse(lines.asSequence())
        } else {
            // Use "shell", after detecting older implementations that use `\r\n` for new lines
            val text = deviceServices.shell(device, "echo foo", TextShellCollector(), stripCrLf = false).first()
            val stripCrLf = text.endsWith("\r\n")
            val lines = deviceServices.shell(device, "getprop", LineShellCollector(), stripCrLf = stripCrLf).toList()
            return DevicePropertiesParser().parse(lines.asSequence())
        }
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
        val api = allReadonly()[RO_BUILD_VERSION_SDK]
        if (api == null) {
            thisLogger(this.session).info {
                "Property '$RO_BUILD_VERSION_SDK' not found, returning $default instead"
            }
            return default
        }
        return try {
            api.toInt()
        } catch (e: NumberFormatException) {
            thisLogger(this.session).info {
                "Property '$RO_BUILD_VERSION_SDK' (\"$api\") is not a number, returning $default instead"
            }
            return default
        }
    }
}
