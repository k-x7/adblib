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
package com.android.adblib.impl.services

/**
 * Defines what kind of data an ADB query is expected to send after an "OKAY" response.
 * See [AdbServiceRunner.runHostDeviceQuery] and [AdbServiceRunner.runHostQuery].
 */
internal enum class OkayDataExpectation {
    /** Data is always expected to be present */
    EXPECTED,
    /** No data is expected to be present */
    NOT_EXPECTED,
    /** Data is sometimes present, sometimes not */
    OPTIONAL,
}
