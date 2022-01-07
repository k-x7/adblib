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
 * Representation of a forward socket connection. See [AdbHostServices.forward] and
 * [AdbHostServices.listForward].
 */
data class ForwardSocketInfo(
    /**
     * The serial number of the device that the [local] socket  forwards to.
     */
    val deviceSerial: String,
    /**
     * The [SocketSpec] corresponding to the server socket connection on the local machine.
     */
    val local: SocketSpec,
    /**
     * The [SocketSpec] corresponding to the socket to open on the remote device.
     */
    val remote: SocketSpec
)

/**
 * The representation of the list of [forward socket entries][ForwardSocketInfo] maintained by the ADB host.
 * See [AdbHostServices.listForward].
 */
typealias ForwardSocketList = ListWithErrors<ForwardSocketInfo>
