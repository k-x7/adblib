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

import com.android.adblib.MdnsCheckResult
import com.android.adblib.MdnsStatus
import com.android.adblib.utils.AdbProtocolUtils.ADB_NEW_LINE

internal class MdnsCheckParser {

    fun parse(text: CharSequence): MdnsCheckResult {
        // ADB Host code, Bonjour implementation
        // https://cs.android.com/android/platform/superproject/+/fbcbf2500b2887952f862fa882741f80464bdbca:packages/modules/adb/client/mdnsresponder_client.cpp;l=564

        // ADB Host code, OpenScreen implementation
        // https://cs.android.com/android/platform/superproject/+/fbcbf2500b2887952f862fa882741f80464bdbca:packages/modules/adb/client/transport_mdns.cpp;drc=fbcbf2500b2887952f862fa882741f80464bdbca;l=278
        text.split(ADB_NEW_LINE).forEach { line ->
            if (line.startsWith("mdns daemon version")) {
                return@parse MdnsCheckResult(MdnsStatus.Enabled, line)
            }
        }

        return MdnsCheckResult(MdnsStatus.Disabled, text.toString())
    }
}
