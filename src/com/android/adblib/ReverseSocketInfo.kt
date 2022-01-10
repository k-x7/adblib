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

/**
 * Representation of a reverse socket connection. See [AdbDeviceServices.reverseForward] and
 * [AdbDeviceServices.reverseListForward].
 */
data class ReverseSocketInfo(
    /**
     * Internal name the device uses to identify the underlying transport type.
     *
     * See ["UsbFfs"](https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/daemon/usb.cpp;l=759)
     */
    val transportName: String,
    /**
     * The [SocketSpec] corresponding to the server socket connection on the device.
     */
    val remote: SocketSpec,
    /**
     * The [SocketSpec] corresponding to the socket connection to open on the host machine.
     */
    val local: SocketSpec
)

/**
 * The representation of the list of [forward socket entries][ReverseSocketInfo] maintained
 * by the ADB host. See [AdbDeviceServices.reverseListForward].
 */
typealias ReverseSocketList = ListWithErrors<ReverseSocketInfo>
