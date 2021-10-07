package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.forwardTo
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import com.android.adblib.utils.TimeoutTracker.Companion.INFINITE
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
            val service = getShellServiceString(command)
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

    private fun getShellServiceString(command: String): String {
        // Shell service string can look like: shell[,arg1,arg2,...]:[command].
        // See https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=594;drc=fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f

        // We don't escape here, just like ssh(1). http://b/20564385.
        // See https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=776
        return "shell:$command"
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
}
