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

import com.android.adblib.AppProcessEntry
import com.android.server.adb.protos.AppProcessesProto
import java.nio.ByteBuffer

/**
 * Parser of list of process entries as sent by the `track-app` service.
 */
internal class AppProcessEntryListParser {

    fun parse(buffer: ByteBuffer): List<AppProcessEntry> {
        val result = mutableListOf<AppProcessEntry>()

        // Special case of <no devices>
        if (!buffer.hasRemaining()) {
            return result
        }

        val appProcesses = AppProcessesProto.AppProcesses.parseFrom(buffer)
        appProcesses.processList.forEach {
            result.add(
                AppProcessEntry(
                    pid = it.pid.toInt(),
                    debuggable = it.debuggable,
                    profileable = it.profileable,
                    architecture = it.architecture
                )
            )
        }

        return result
    }
}
