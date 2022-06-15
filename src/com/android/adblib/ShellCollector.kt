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
 * A [ShellCollector] is responsible for mapping raw binary output of a shell command,
 * provided as [ByteBuffer] instances, and emit mapped value to a [FlowCollector] of
 * type [T].
 *
 * @see [AdbDeviceServices.shellCommand]
 * @see [AdbDeviceServices.shell]
 * @see [AdbDeviceServices.exec]
 * @see [AdbDeviceServices.abb_exec]
 */
interface ShellCollector<T> {

    /**
     * Invoked by [AdbDeviceServices.shell] as soon as the shell command execution has started
     * on the device, but before any output from `stdout` has been processed.
     *
     * [collector] The [FlowCollector] where flow elements should be emitted, if any.
     */
    suspend fun start(collector: FlowCollector<T>)

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
