package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.forwardTo
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import com.android.adblib.utils.TimeoutTracker.Companion.INFINITE
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class AdbDeviceServicesImpl(
    private val host: AdbLibHost,
    channelProvider: AdbChannelProvider,
    private val timeout: Long,
    private val unit: TimeUnit
) : AdbDeviceServices {

    private val serviceRunner = AdbServiceRunner(host, channelProvider)

    override fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel?,
        commandTimeout: Duration,
        bufferSize: Int,
    ): Flow<T> = flow {
        host.logger.info("Device \"${device}\" - Start execution of shell command \"$command\" (bufferSize=$bufferSize bytes)")
        // Note: We only track the time to launch the shell command, since command execution
        // itself can take an arbitrary amount of time.
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val workBuffer = serviceRunner.newResizableBuffer()
        val (channel, transportId) = serviceRunner.switchToTransport(device, workBuffer, tracker)
        channel.use {
            // We switched the channel to the right transport (i.e. device), now send the service request
            val service = getShellServiceString(ShellProtocol.V1, command)
            host.logger.debug("\"${service}\" - sending local service request to ADB daemon, timeout: $tracker")
            serviceRunner.sendAbdServiceRequest(channel, workBuffer, service, tracker)
            serviceRunner.consumeOkayFailResponse(channel, workBuffer, tracker)

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
                    transportId,
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
        val workBuffer = serviceRunner.newResizableBuffer()
        val (channel, transportId) = serviceRunner.switchToTransport(device, workBuffer, tracker)
        channel.use {
            // We switched the channel to the right transport (i.e. device), now send the service request
            val localService = getShellServiceString(ShellProtocol.V2, command)
            host.logger.debug("\${localService}\" - sending local service request to ADB daemon, timeout: $tracker")
            serviceRunner.sendAbdServiceRequest(channel, workBuffer, localService, tracker)
            serviceRunner.consumeOkayFailResponse(channel, workBuffer, tracker)

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
                    localService,
                    transportId,
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
        transportId: Long?,
        bufferSize: Int,
        shellCollector: ShellCollector<T>,
        flowCollector: FlowCollector<T>
    ) {
        host.logger.debug("\"${service}\" - Collecting messages from shell command output")
        shellCollector.start(flowCollector, transportId)
        while (true) {
            host.logger.verbose("\"${service}\" - Waiting for next message from shell command output")

            // Note: We use an infinite timeout here as shell commands can take arbitrary amount
            //       of time to execute and produce output.
            workBuffer.clear()
            val byteCount = channel.read(workBuffer.forChannelRead(bufferSize), INFINITE)
            if (byteCount < 0) {
                // We are done reading from this channel
                break
            }
            val buffer = workBuffer.afterChannelRead()
            assert(buffer.remaining() == byteCount)

            host.logger.verbose("\"${service}\" - Emitting packet of $byteCount bytes")
            shellCollector.collect(flowCollector, buffer)
        }
        shellCollector.end(flowCollector)
        host.logger.debug("\"${service}\" - Done collecting messages from shell command output")
    }

    private suspend fun <T> collectShellCommandOutputV2Format(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        transportId: Long?,
        shellCollector: ShellV2Collector<T>,
        flowCollector: FlowCollector<T>
    ) {
        host.logger.debug("\"${service}\" - Waiting for next shell protocol packet")
        shellCollector.start(flowCollector, transportId)
        val shellProtocol = ShellV2ProtocolHandler(channel, workBuffer)

        while (true) {
            // Note: We use an infinite timeout here, as the only wait to end this request is to close
            //       the underlying ADB socket channel. This is by design.
            val (packetKind, packetBuffer) = shellProtocol.readPacket(INFINITE)
            when (packetKind) {
                ShellV2PacketKind.STDOUT -> {
                    host.logger.debug("Received stdout buffer of ${packetBuffer.remaining()} bytes")
                    shellCollector.collectStdout(flowCollector, packetBuffer)
                }
                ShellV2PacketKind.STDERR -> {
                    host.logger.debug("Received stderr buffer of ${packetBuffer.remaining()} bytes")
                    shellCollector.collectStderr(flowCollector, packetBuffer)
                }
                ShellV2PacketKind.EXIT_CODE -> {
                    // Ensure value is unsigned
                    val exitCode = packetBuffer.get().toInt() and 0xFF
                    host.logger.debug("Received shell command exit code=${exitCode}")
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
            host.logger.debug("\"${service}\" - packet processed successfully")
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
        stdInput.forwardTo(host, shellCommandChannel, bufferSize)
        host.logger.info("forwardStdInput - input channel has reached EOF, sending EOF to shell host")
        shellCommandChannel.shutdownOutput()
    }

    private suspend fun forwardStdInputV2Format(
        deviceChannel: AdbChannel,
        stdInput: AdbInputChannel,
        bufferSize: Int
    ) {
        // Note: We use an infinite timeout here, as the only wait to end this request is to close
        //       the underlying ADB socket channel, or for `stdin` to reach EOF. This is by design.
        val timeout = INFINITE

        val workBuffer = serviceRunner.newResizableBuffer()
        val shellProtocol = ShellV2ProtocolHandler(deviceChannel, workBuffer)

        while (true) {
            // Reserve the bytes needed for the packet header
            val buffer = shellProtocol.prepareWriteBuffer(bufferSize)

            // Read data from stdin
            val byteCount = stdInput.read(buffer, timeout)
            if (byteCount < 0) {
                // EOF, job is finished
                shellProtocol.writePreparedBuffer(ShellV2PacketKind.CLOSE_STDIN, timeout)
                break
            }
            // Buffer contains packet header + data
            shellProtocol.writePreparedBuffer(ShellV2PacketKind.STDIN, timeout)
        }
    }

    private enum class ShellProtocol {
        V1,
        V2
    }
}
