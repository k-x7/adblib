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
package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbLibSession
import com.android.adblib.DeviceSelector
import com.android.adblib.ProcessIdList
import com.android.adblib.ReverseSocketList
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.SocketSpec
import com.android.adblib.forwardTo
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.impl.services.OkayDataExpectation
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.impl.TimeoutTracker.Companion.INFINITE
import com.android.adblib.impl.services.TrackJdwpService
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class AdbDeviceServicesImpl(
    override val session: AdbLibSession,
    channelProvider: AdbChannelProvider,
    private val timeout: Long,
    private val unit: TimeUnit
) : AdbDeviceServices {
    private val myReverseSocketListParser = ReverseSocketListParser()

    private val host: AdbLibHost
        get() = session.host

    private val serviceRunner = AdbServiceRunner(session, channelProvider)
    private val trackJdwpService = TrackJdwpService(serviceRunner)

    override fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> = flow {
        host.logger.info { "Device \"${device}\" - Start execution of shell command \"$command\" (bufferSize=$bufferSize bytes)" }
        // Note: We only track the time to launch the shell command, since command execution
        // itself can take an arbitrary amount of time.
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = getShellServiceString(ShellProtocol.V1, command)
        serviceRunner.runDaemonService(device, service, tracker) { channel, workBuffer ->
            host.timeProvider.withErrorTimeout(commandTimeout) {
                // Forward `stdin` from channel to adb (in a new coroutine so that we
                // can also collect `stdout` concurrently)
                stdinChannel?.let {
                    launch {
                        forwardStdInput(channel, stdinChannel, bufferSize)
                    }
                }

                // Forward `stdout` from adb to flow
                collectShellCommandOutput(
                    channel,
                    workBuffer,
                    service,
                    bufferSize,
                    shellCollector,
                    this@flow
                )
            }
        }
    }.flowOn(host.ioDispatcher)

    override suspend fun sync(device: DeviceSelector): AdbDeviceSyncServices {
        return AdbDeviceSyncServicesImpl.open(serviceRunner, device, timeout, unit)
    }

    override suspend fun reverseListForward(device: DeviceSelector): ReverseSocketList {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=986
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1876
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:list-forward"
        val data = serviceRunner.runDaemonQuery(device, service, tracker)
        return myReverseSocketListParser.parse(data)
    }

    override suspend fun reverseForward(
        device: DeviceSelector,
        remote: SocketSpec,
        local: SocketSpec,
        rebind: Boolean
    ): String? {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:forward:" +
                (if (rebind) "" else "norebind:") +
                remote.toQueryString() +
                ";" +
                local.toQueryString()
        return serviceRunner.runDaemonQuery2(device, service, tracker, OkayDataExpectation.OPTIONAL)
    }

    override suspend fun reverseKillForward(device: DeviceSelector, remote: SocketSpec) {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1006
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1895
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:killforward:${remote.toQueryString()}"
        serviceRunner.runDaemonQuery2(
            device,
            service,
            tracker,
            OkayDataExpectation.NOT_EXPECTED
        )
    }

    override suspend fun reverseKillForwardAll(device: DeviceSelector) {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=996
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/commandline.cpp;l=1895
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "reverse:killforward-all"
        serviceRunner.runDaemonQuery2(
            device,
            service,
            tracker,
            OkayDataExpectation.NOT_EXPECTED
        )
    }

    override fun trackJdwp(device: DeviceSelector): Flow<ProcessIdList> {
        return trackJdwpService.invoke(device, timeout, unit)
    }

    override fun <T> shellV2(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int
    ): Flow<T> = flow {
        // Note: We only track the time to launch the shell command, since command execution
        // itself can take an arbitrary amount of time.
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        // We switched the channel to the right transport (i.e. device), now send the service request
        val service = getShellServiceString(ShellProtocol.V2, command)
        serviceRunner.runDaemonService(device, service, tracker) { channel, workBuffer ->
            host.timeProvider.withErrorTimeout(commandTimeout) {
                // Forward `stdin` from channel to adb (in a new coroutine so that we
                // can also collect `stdout` concurrently)
                stdinChannel?.let {
                    coroutineScope {
                        launch {
                            forwardStdInputV2Format(channel, stdinChannel, bufferSize)
                        }
                    }
                }

                // Forward `stdout` and `stderr` from adb to flow
                collectShellCommandOutputV2Format(
                    channel,
                    workBuffer,
                    service,
                    shellCollector,
                    this@flow
                )
            }
        }
    }.flowOn(host.ioDispatcher)

    private suspend fun <T> collectShellCommandOutput(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        bufferSize: Int,
        shellCollector: ShellCollector<T>,
        flowCollector: FlowCollector<T>
    ) {
        host.logger.debug { "\"${service}\" - Collecting messages from shell command output" }
        shellCollector.start(flowCollector)
        while (true) {
            host.logger.verbose { "\"${service}\" - Waiting for next message from shell command output" }

            // Note: We use an infinite timeout here as shell commands can take arbitrary amount
            //       of time to execute and produce output.
            workBuffer.clear()
            val byteCount = channel.read(workBuffer.forChannelRead(bufferSize))
            if (byteCount < 0) {
                // We are done reading from this channel
                break
            }
            val buffer = workBuffer.afterChannelRead()
            assert(buffer.remaining() == byteCount)

            host.logger.verbose { "\"${service}\" - Emitting packet of $byteCount bytes" }
            shellCollector.collect(flowCollector, buffer)
        }
        shellCollector.end(flowCollector)
        host.logger.debug { "\"${service}\" - Done collecting messages from shell command output" }
    }

    private suspend fun <T> collectShellCommandOutputV2Format(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        shellCollector: ShellV2Collector<T>,
        flowCollector: FlowCollector<T>
    ) {
        host.logger.debug { "\"${service}\" - Waiting for next shell protocol packet" }
        shellCollector.start(flowCollector)
        val shellProtocol = ShellV2ProtocolHandler(channel, workBuffer)

        while (true) {
            // Note: We use an infinite timeout here, as the only wait to end this request is to close
            //       the underlying ADB socket channel. This is by design.
            val (packetKind, packetBuffer) = shellProtocol.readPacket(INFINITE)
            when (packetKind) {
                ShellV2PacketKind.STDOUT -> {
                    host.logger.debug { "Received stdout buffer of ${packetBuffer.remaining()} bytes" }
                    shellCollector.collectStdout(flowCollector, packetBuffer)
                }
                ShellV2PacketKind.STDERR -> {
                    host.logger.debug { "Received stderr buffer of ${packetBuffer.remaining()} bytes" }
                    shellCollector.collectStderr(flowCollector, packetBuffer)
                }
                ShellV2PacketKind.EXIT_CODE -> {
                    // Ensure value is unsigned
                    val exitCode = packetBuffer.get().toInt() and 0xFF
                    host.logger.debug { "Received shell command exit code=${exitCode}" }
                    shellCollector.end(flowCollector, exitCode)

                    // There should be no messages after the exit code
                    break
                }
                ShellV2PacketKind.STDIN,
                ShellV2PacketKind.CLOSE_STDIN,
                ShellV2PacketKind.WINDOW_SIZE_CHANGE,
                ShellV2PacketKind.INVALID -> {
                    host.logger.warn("Skipping shell protocol packet (kind=\"${packetKind}\")")
                }
            }
            host.logger.debug { "\"${service}\" - packet processed successfully" }
        }
    }

    private fun getShellServiceString(shellProtocol: ShellProtocol, command: String): String {
        // Shell service string can look like: shell[,arg1,arg2,...]:[command].
        // See https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=594;drc=fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f

        // We don't escape here, just like ssh(1). http://b/20564385.
        // See https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=776
        return when (shellProtocol) {
            ShellProtocol.V1 -> "shell:$command"
            ShellProtocol.V2 -> "shell,v2:$command"
        }
    }

    private suspend fun forwardStdInput(
        shellCommandChannel: AdbChannel,
        stdInput: AdbInputChannel,
        bufferSize: Int
    ) {
        stdInput.forwardTo(session, shellCommandChannel, bufferSize)
        host.logger.info { "forwardStdInput - input channel has reached EOF, sending EOF to shell host" }
        shellCommandChannel.shutdownOutput()
    }

    private suspend fun forwardStdInputV2Format(
        deviceChannel: AdbChannel,
        stdInput: AdbInputChannel,
        bufferSize: Int
    ) {
        val workBuffer = serviceRunner.newResizableBuffer()
        val shellProtocol = ShellV2ProtocolHandler(deviceChannel, workBuffer)

        while (true) {
            // Reserve the bytes needed for the packet header
            val buffer = shellProtocol.prepareWriteBuffer(bufferSize)

            // Read data from stdin
            // Note: We use an infinite timeout here, as the only wait to end this request is to close
            //       the underlying ADB socket channel, or for `stdin` to reach EOF. This is by design.
            val byteCount = stdInput.read(buffer)
            if (byteCount < 0) {
                // EOF, job is finished
                shellProtocol.writePreparedBuffer(ShellV2PacketKind.CLOSE_STDIN)
                break
            }
            // Buffer contains packet header + data
            shellProtocol.writePreparedBuffer(ShellV2PacketKind.STDIN)
        }
    }

    private enum class ShellProtocol {
        V1,
        V2
    }
}
