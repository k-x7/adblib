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

import com.android.adblib.ListWithErrors
import com.android.adblib.ProcessIdList
import com.android.adblib.utils.AdbProtocolUtils

/**
 * Parser of list of process IDs as sent by the `track-jdwp` service.
 */
internal class ProcessIdListParser {

    fun parse(responseText: CharSequence): ProcessIdList {
        val result = ListWithErrors.Builder<Int>()

        // Special case of <no devices>
        if (responseText.isEmpty()) {
            return result.build()
        }

        // There should be one device per line
        val lines = responseText.split(AdbProtocolUtils.ADB_NEW_LINE)
        for ((lineIndex, line) in lines.withIndex()) {
            if (line.isNotEmpty()) {
                val pid = line.toIntOrNull()
                if (pid == null) {
                    result.addError("Invalid process ID", lineIndex, line)
                } else {
                    result.addEntry(pid)
                }
            }
        }
        return result.build()
    }
}
