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
package com.android.adblib

import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * A collector of output from the shell command execution on a device, see [AdbDeviceServices.shell]
 */
interface ShellCollector<T> {

    /**
     * Invoked by [AdbDeviceServices.shell] as soon as the shell command execution has started
     * on the device, but before any output from `stdout` has been processed.
     *
     * [collector] The [FlowCollector] where flow elements should be emitted, if any.
     *
     * [transportId] The (optional) transport ID of the device if the [DeviceSelector]
     * used to start the shell command specified that a transport ID should be returned
     * on the channel. `null` otherwise.
     */
    suspend fun start(collector: FlowCollector<T>, transportId: Long?)

    /**
     * Process a single [ByteBuffer] received from `stdout` of the shell command.
     *
     * [collector] The [FlowCollector] where flow elements should be emitted, if any.
     *
     * [stdout] The [ByteBuffer] containing a chunk of bytes collected from `stdout`.
     * For performance reasons, the buffer is only valid during the method call so the data must
     * be consumed directly in this method implementation.
     */
    suspend fun collect(collector: FlowCollector<T>, stdout: ByteBuffer)

    /**
     * Invoked when `stdout` from the command shell has reached EOF, i.e. when the command
     * execution has ended.
     *
     * [collector] The [FlowCollector] where leftover flow elements should be emitted, if any.
     */
    suspend fun end(collector: FlowCollector<T>)
}
