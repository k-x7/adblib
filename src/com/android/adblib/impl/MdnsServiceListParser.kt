/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.adblib.DeviceAddress
import com.android.adblib.ErrorLine
import com.android.adblib.ListWithErrors
import com.android.adblib.MdnsServiceInfo
import com.android.adblib.MdnsServiceList
import com.android.adblib.utils.AdbProtocolUtils.ADB_NEW_LINE

private const val ALL_BUT_TAB = "[^\t]+"
private const val TAB = "\\t"

/**
 * Output example
 *
 *  ```
 *     adb-939AX05XBZ-vWgJpq	_adb-tls-connect._tcp.	192.168.1.86:39149
 *     adb-939AX05XBZ-vWgJpq	_adb-tls-pairing._tcp.	192.168.1.86:37313
 *  ```
 *
 * Regular expression
 *
 *  `<everything>TAB<everything>TAB<everything>`
 */
private const val SERVICE_LINE_PATTERN =
    "(${ALL_BUT_TAB})${TAB}(${ALL_BUT_TAB})${TAB}(${ALL_BUT_TAB})"

internal class MdnsServiceListParser {

    private val lineRegex = Regex(SERVICE_LINE_PATTERN)

    fun parse(text: CharSequence): MdnsServiceList {
        val builder = ListWithErrors.Builder<MdnsServiceInfo>()

        // ADB Host code, Bonjour implementation
        // https://cs.android.com/android/platform/superproject/+/fbcbf2500b2887952f862fa882741f80464bdbca:packages/modules/adb/client/mdnsresponder_client.cpp;l=576

        // ADB Host code, OpenScreen implementation
        // https://cs.android.com/android/platform/superproject/+/fbcbf2500b2887952f862fa882741f80464bdbca:packages/modules/adb/client/transport_mdns.cpp;l=290;drc=fbcbf2500b2887952f862fa882741f80464bdbca
        text.split(ADB_NEW_LINE)
            .filter { it.trim().isNotBlank() }
            .forEachIndexed { lineIndex, line ->
                val matchResult = lineRegex.find(line)

                if (matchResult == null) {
                    val error =
                        ErrorLine("mDNS service entry format not recognized", lineIndex, line)
                    builder.addError(error)
                    return@forEachIndexed
                }

                try {
                    val instanceName = matchResult.groupValues[1]
                    val serviceName = matchResult.groupValues[2]
                    val deviceAddress = DeviceAddress(matchResult.groupValues[3])
                    builder.addEntry(MdnsServiceInfo(instanceName, serviceName, deviceAddress))
                } catch (ignored: Exception) {
                    val error =
                        ErrorLine(
                            "mDNS service entry ignored due do invalid characters",
                            lineIndex,
                            line
                        )
                    builder.addError(error)
                }
            }

        return builder.build()
    }
}
