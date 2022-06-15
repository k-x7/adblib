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

import com.android.adblib.utils.LineBatchShellV2Collector
import com.android.adblib.utils.LineShellV2Collector
import com.android.adblib.utils.TextShellV2Collector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Supports customization of various aspects of the execution of a shell command on a device,
 * including automatically falling back from [AdbDeviceServices.shellV2] to legacy protocols
 * on older devices.
 *
 * Once a [ShellCommand] is configured with various `withXxx` methods, use the [execute]
 * method to launch the shell command execution, returning a [Flow&lt;T&gt;][Flow].
 *
 * @see [AdbDeviceServices.shellCommand]
 * @see [AdbDeviceServices.shellV2]
 * @see [AdbDeviceServices.exec]
 * @see [AdbDeviceServices.shell]
 */
interface ShellCommand<T> {

    /**
     * Applies a [ShellV2Collector] to transfer the raw binary shell command output.
     * This change the type of this [ShellCommand] from [T] to the final target type [U].
     */
    fun <U> withCollector(collector: ShellV2Collector<U>): ShellCommand<U>

    /**
     * Applies a legacy [ShellCollector] to transfer the raw binary shell command output.
     * This change the type of this [ShellCommand] from [T] to the final target type [U].
     */
    fun <U> withLegacyCollector(collector: ShellCollector<U>): ShellCommand<U>

    /**
     * The [AdbInputChannel] to send to the device for `stdin`.
     *
     * The default value is `null`.
     */
    fun withStdin(stdinChannel: AdbInputChannel?): ShellCommand<T>

    /**
     * Applies a [timeout] that triggers [TimeoutException] exception if the shell command
     * does not terminate within the specified [Duration].
     *
     * The default value is [INFINITE_DURATION].
     */
    fun withCommandTimeout(timeout: Duration): ShellCommand<T>

    /**
     * Applies a [timeout] that triggers a [TimeoutException] exception when the command does
     * not generate any output (`stdout` or `stderr`) for the specified [Duration].
     *
     * The default value is [INFINITE_DURATION].
     */
    fun withCommandOutputTimeout(timeout: Duration): ShellCommand<T>

    /**
     * Overrides the default buffer size used for buffering `stdout`, `stderr` and `stdin`.
     *
     * The default value is [DEFAULT_SHELL_BUFFER_SIZE].
     */
    fun withBufferSize(size: Int): ShellCommand<T>

    /**
     * Allows [execute] to use [AdbDeviceServices.shellV2] if available.
     *
     * The default value is `true`.
     */
    fun allowShellV2(value: Boolean): ShellCommand<T>

    /**
     * Allows [execute] to fall back to [AdbDeviceServices.exec] if [AdbDeviceServices.shellV2]
     * is not available or not allowed.
     *
     * The default value is `true`.
     */
    fun allowLegacyExec(value: Boolean): ShellCommand<T>

    /**
     * Allows [execute] to fall back to [AdbDeviceServices.shell] if [AdbDeviceServices.shellV2]
     * and [AdbDeviceServices.exec] are not available or not allowed.
     *
     * The default value is `true`.
     */
    fun allowLegacyShell(value: Boolean): ShellCommand<T>

    /**
     * Allows overriding the shell command to [execute] on the device just before
     * execution starts, when the [Protocol] to be used is known.
     *
     * This can be useful, for example, for providing custom shell handling in case
     * [AdbDeviceServices.shellV2] is not supported and [execute] has to fall back to
     * [AdbDeviceServices.exec].
     *
     * The default value is a `no-op`.
     */
    fun withCommandOverride(commandOverride: (String, Protocol) -> String): ShellCommand<T>

    /**
     * Returns a [Flow] that executes the shell command on the device, according to the
     * various customization rules set by the `withXxx` methods.
     *
     * If [withCollector] or [withLegacyCollector] was not invoked before [execute],
     * an [IllegalArgumentException] is thrown, as a shell collector is mandatory.
     *
     * Once [execute] is called, further customization is not allowed.
     */
    fun execute(): Flow<T>

    /**
     * The protocol used for [executing][execute] a [ShellCommand]
     */
    enum class Protocol {
        SHELL_V2,
        SHELL,
        EXEC
    }
}

fun <T> ShellCommand<T>.withLineCollector(): ShellCommand<ShellCommandOutputElement> {
    return this.withCollector(LineShellV2Collector())
}

fun <T> ShellCommand<T>.withLineBatchCollector(): ShellCommand<BatchShellCommandOutputElement> {
    return this.withCollector(LineBatchShellV2Collector())
}

fun <T> ShellCommand<T>.withTextCollector(): ShellCommand<ShellCommandOutput> {
    return this.withCollector(TextShellV2Collector())
}
