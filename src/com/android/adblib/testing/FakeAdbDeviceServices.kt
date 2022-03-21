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
package com.android.adblib.testing

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.ProcessIdList
import com.android.adblib.ReverseSocketList
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.SocketSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Duration

/**
 * A fake implementation of [AdbDeviceServices] for tests.
 */
class FakeAdbDeviceServices(override val session: AdbLibSession) : AdbDeviceServices {

    private val shellCommands = mutableMapOf<String, MutableMap<String, Any>>()

    private val shellV2Commands = mutableMapOf<String, MutableMap<String, Any>>()

    /**
     * Configure a [shell] service request.
     *
     * @param deviceSelector A device the command is executed on
     * @param command a command executed on a device
     * @param result the result of a command
     */
    fun <T> configureShellCommand(deviceSelector: DeviceSelector, command: String, result: T) {
        shellCommands.configureCommand(deviceSelector, command, result as Any)
    }

    /**
     * Configure a [shell] service request.
     *
     * @param deviceSelector A device the command is executed on
     * @param command a command executed on a device
     * @param result the result of a command
     */
    fun <T> configureShellV2Command(deviceSelector: DeviceSelector, command: String, result: T) {
        shellV2Commands.configureCommand(deviceSelector, command, result as Any)
    }

    override fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> {
        val result = (shellCommands[device.transportPrefix]?.get(command)
            ?: throw IllegalStateException("""Command not setup for $device: "$command""""))
        @Suppress("UNCHECKED_CAST")
        return flowOf(result as T)
    }

    override fun <T> exec(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int
    ): Flow<T> {
        TODO("Not yet implemented")
    }

    override fun <T> shellV2(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> {
        val result = (shellV2Commands[device.transportPrefix]?.get(command)
            ?: throw IllegalStateException("""Command not setup for $device: "$command""""))
        @Suppress("UNCHECKED_CAST")
        return flowOf(result as T)
    }

    override fun <T> abb_exec(
        device: DeviceSelector,
        command: List<String>,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int
    ): Flow<T> {
        TODO("Not yet implemented")
    }

    override suspend fun sync(device: DeviceSelector): AdbDeviceSyncServices {
        TODO("Not yet implemented")
    }

    override suspend fun reverseListForward(device: DeviceSelector): ReverseSocketList {
        TODO("Not yet implemented")
    }

    override suspend fun reverseForward(
        device: DeviceSelector,
        remote: SocketSpec,
        local: SocketSpec,
        rebind: Boolean
    ): String? {
        TODO("Not yet implemented")
    }

    override suspend fun reverseKillForward(device: DeviceSelector, remote: SocketSpec) {
        TODO("Not yet implemented")
    }

    override suspend fun reverseKillForwardAll(device: DeviceSelector) {
        TODO("Not yet implemented")
    }

    override fun trackJdwp(device: DeviceSelector): Flow<ProcessIdList> {
        TODO("Not yet implemented")
    }
}

private fun <T> MutableMap<String, MutableMap<String, T>>.configureCommand(
    deviceSelector: DeviceSelector,
    command: String,
    result: T
) {
    getOrPut(deviceSelector.transportPrefix) { mutableMapOf() }[command] = result
}
