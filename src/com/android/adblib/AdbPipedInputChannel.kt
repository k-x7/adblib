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
 * An [AdbInputChannel] that can be fed data asynchronously from its [pipeSource] property,
 * an [AdbOutputChannel] implementation that writes data for use by [read] operations
 * on this [AdbPipedInputChannel].
 *
 * Note: Implementations are guaranteed to be thread-safe.
 */
interface AdbPipedInputChannel : AdbInputChannel {

    /**
     * The [AdbOutputChannel] used to [send][AdbOutputChannel.write] data to this input pipe
     */
    val pipeSource: AdbOutputChannel
}
