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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.ProcessIdList
import com.android.adblib.ReverseSocketList
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.SocketSpec
import com.android.adblib.utils.AdbProtocolUtils.ADB_CHARSET
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.min

/**
 * A fake implementation of [AdbDeviceServices] for tests.
 */
class FakeAdbDeviceServices(override val session: AdbLibSession) : AdbDeviceServices {

    /**
     * Maps device -> ShellCommands
     */
    private val shellCommands = mutableMapOf<String, ShellCommands>()

    /**
     * Maps device -> ShellV2Commands
     */
    private val shellV2Commands = mutableMapOf<String, ShellV2Commands>()

    /**
     * A record of all calls to [shell]
     */
    val shellRequests = LinkedBlockingDeque<ShellRequest>()

    /**
     * A record of all calls to [shellV2]
     */
    val shellV2Requests = LinkedBlockingDeque<ShellRequest>()

    /**
     * Configure a [shell] service request.
     *
     * @param deviceSelector A device the command is executed on
     * @param command a command executed on a device
     * @param result the result of a command
     */
    fun configureShellCommand(deviceSelector: DeviceSelector, command: String, result: String) {
        shellCommands.getOrPut(deviceSelector.transportPrefix) { ShellCommands() }
            .add(command, result)
    }

    /**
     * Configure a [shellV2] service request.
     *
     * @param deviceSelector A device the command is executed on
     * @param command a command executed on a device
     * @param stdout the standard output of a command
     * @param stderr the standard error of a command
     * @param exitCode the exit code of a command
     */
    fun configureShellV2Command(
        deviceSelector: DeviceSelector,
        command: String,
        stdout: String,
        stderr: String = "",
        exitCode: Int = 0,
    ) {
        shellV2Commands.getOrPut(deviceSelector.transportPrefix) { ShellV2Commands() }
            .add(command, stdout, stderr, exitCode)
    }

    override fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput : Boolean
    ): Flow<T> {
        shellRequests.add(ShellRequest(device.toString(), command, commandTimeout, bufferSize))
        val output = shellCommands[device.transportPrefix]?.get(command)
            ?: throw IllegalStateException("""Command not setup for $device: "$command"""")
        return flow {
            shellCollector.start(this)
            output.split(bufferSize) { shellCollector.collect(this, it) }
            shellCollector.end(this)
        }
    }

    override fun <T> exec(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput : Boolean
    ): Flow<T> {
        return shell(device, command, shellCollector, stdinChannel, commandTimeout, bufferSize, shutdownOutput)
    }

    override fun <T> shellV2(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> {
        shellV2Requests.add(ShellRequest(device.toString(), command, commandTimeout, bufferSize))
        val output = shellV2Commands[device.transportPrefix]?.get(command)
            ?: throw IllegalStateException("""Command not setup for $device: "$command"""")
        return flow {
            shellCollector.start(this)
            output.stdout.split(bufferSize) { shellCollector.collectStdout(this, it) }
            output.stderr.split(bufferSize) { shellCollector.collectStderr(this, it) }
            shellCollector.end(this, output.exitCode)
        }
    }

    override fun <T> abb_exec(
        device: DeviceSelector,
        args: List<String>,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
        shutdownOutput : Boolean
    ): Flow<T> {
        TODO("Not yet implemented")
    }

    override fun <T> abb(
        device: DeviceSelector,
        args: List<String>,
        shellCollector: ShellV2Collector<T>,
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

    override suspend fun jdwp(device: DeviceSelector, pid: Int): AdbChannel {
        TODO("Not yet implemented")
    }

    private class ShellCommands {

        private val commands = mutableMapOf<String, ByteBuffer>()

        fun add(command: String, stdout: ByteBuffer) {
            commands[command] = stdout
        }

        fun add(command: String, stdout: String) {
            add(command, stdout.toByteBuffer())
        }

        fun get(command: String): ByteBuffer? = commands[command]
    }

    class ShellV2Output(val stdout: ByteBuffer, val stderr: ByteBuffer, val exitCode: Int)

    private class ShellV2Commands {

        private val commands = mutableMapOf<String, ShellV2Output>()
        fun add(command: String, stdout: ByteBuffer, stderr: ByteBuffer, exitCode: Int) {
            commands[command] = ShellV2Output(stdout, stderr, exitCode)
        }

        fun add(command: String, stdout: String, stderr: String, exitCode: Int) {
            add(command, stdout.toByteBuffer(), stderr.toByteBuffer(), exitCode)
        }

        fun get(command: String): ShellV2Output? = commands[command]
    }

    /**
     * Details of a call to [shell] or [shellV2]
     */
    data class ShellRequest(
        val deviceSelector: String,
        val command: String,
        val commandTimeout: Duration = INFINITE_DURATION,
        val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    )
}

private fun String.toByteBuffer() = ByteBuffer.wrap(toByteArray(ADB_CHARSET))

/**
 * split the [ByteBuffer.remaining] bytes of this buffer into array chunks of [chunkSize] bytes. The
 * buffer position and limit remain unchanged on exit.
 */
private suspend fun ByteBuffer.split(chunkSize: Int, processChunk: suspend (ByteBuffer) -> Unit) {
    val pos = position()
    while (hasRemaining()) {
        val remaining = remaining()
        val bytes = ByteArray(min(remaining, chunkSize))
        get(bytes)
        processChunk(ByteBuffer.wrap(bytes))
    }
    position(pos)
}
