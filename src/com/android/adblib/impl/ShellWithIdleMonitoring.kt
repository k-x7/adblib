package com.android.adblib.impl

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.time.Duration

internal class ShellWithIdleMonitoring<T>(
    private val host: AdbLibHost,
    private val deviceServices: AdbDeviceServices,
    private val device: DeviceSelector,
    private val command: String,
    private val stdoutCollector: ShellCollector<T>,
    private val stdinChannel: AdbInputChannel?,
    private val commandTimeout: Duration,
    private val commandOutputTimeout: Duration,
    private val bufferSize: Int
) {

    fun flow(): Flow<T> = flow {
        // To detect a command that does not produce any output for the given timeout:
        // 1) we intercept the original ShellCollector, forward all messages and
        //    emit a message to a "heartbeat" channel every time we see output coming in
        // 2) at the same time, we run a concurrent coroutine, the "heartbeat detector" that
        //    checks the "heartbeat" channel for messages. If there is no incoming message
        //    for longer than the timeout, the coroutine throws an `TimeoutException`, which
        //    cancels the whole flow with a `TimeoutException`.
        val heartbeatChannel = Channel<Unit>()
        val forwardingCollector = ForwardingShellCollector(host, heartbeatChannel, stdoutCollector)
        val heartbeatDetector = HeartbeatDetector(host, heartbeatChannel, commandOutputTimeout)
        coroutineScope {
            // Launch our command (in)activity detector
            launch {
                heartbeatDetector.run()
            }

            // Start our regular shell command execution
            val shellFlow =
                deviceServices.shell(
                    device,
                    command,
                    forwardingCollector,
                    stdinChannel,
                    commandTimeout,
                    bufferSize
                )

            // Forward from the regular shell flow to the returned flow
            shellFlow.collect {
                emit(it)
            }

            // Ensure the idle detector exits its loop
            heartbeatChannel.close()
        }
    }.flowOn(host.ioDispatcher)

    /**
     * A [ShellCollector] that forwards all messages to another [ShellCollector]
     * while at the same time emits "heartbeats" on a [SendChannel] when messages
     * are forwarded.
     */
    private class ForwardingShellCollector<T>(
        private val host: AdbLibHost,
        private val heartbeatChannel: SendChannel<Unit>,
        private val delegate: ShellCollector<T>
    ) : ShellCollector<T> {

        private val logPrefix: String
            get() = this::class.java.simpleName

        override suspend fun start(collector: FlowCollector<T>, transportId: Long?) {
            host.logger.verbose { "$logPrefix.start" }
            heartbeatChannel.send(Unit)
            delegate.start(collector, transportId)
        }

        override suspend fun collect(collector: FlowCollector<T>, stdout: ByteBuffer) {
            host.logger.verbose { "$logPrefix.collect" }
            heartbeatChannel.send(Unit)
            delegate.collect(collector, stdout)
        }

        override suspend fun end(collector: FlowCollector<T>) {
            host.logger.verbose { "$logPrefix.end" }
            heartbeatChannel.send(Unit)
            delegate.end(collector)
        }
    }

    /**
     * A coroutine helper class that waits for messages on [heartbeatChannel], ensuring
     * each message is received within the [commandIdleTimeout] delay.
     */
    private class HeartbeatDetector(
        private val host: AdbLibHost,
        private val heartbeatChannel: ReceiveChannel<Unit>,
        private val commandIdleTimeout: Duration
    ) {

        suspend fun run() {
            // Wait until the command is actually executing (without timeout, as this initial
            // message is received after connecting to ADB and setting the command, etc.)
            heartbeatChannel.receive()

            // Make sure we have some activity on the channel within the timeout period
            var active = true
            while (active) {
                host.timeProvider.withErrorTimeout(commandIdleTimeout) {
                    try {
                        // An element on the channel means the shell command emitted output
                        heartbeatChannel.receive()
                    } catch (e: ClosedReceiveChannelException) {
                        // When the channel is closed, we are done and should exit normally
                        active = false
                    }
                }
            }
        }
    }
}
