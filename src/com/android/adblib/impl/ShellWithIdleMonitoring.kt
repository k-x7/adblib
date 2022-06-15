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

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.thisLogger
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

internal abstract class ShellWithIdleMonitoring<T, TShellCollector>(
    private val parameters: Parameters<TShellCollector>
) {

    /**
     * Convenience class used to pass all parameters in one go
     */
    class Parameters<TShellCollector>(
        val deviceServices: AdbDeviceServices,
        val device: DeviceSelector,
        val command: String,
        val shellCollector: TShellCollector,
        val stdinChannel: AdbInputChannel?,
        val commandTimeout: Duration,
        val commandOutputTimeout: Duration,
        val bufferSize: Int
    )

    private val deviceServices: AdbDeviceServices
        get() = parameters.deviceServices

    private val host: AdbLibHost
        get() = deviceServices.session.host

    fun createFlow(): Flow<T> = flow {
        // To detect a command that does not produce any output for the given timeout:
        // 1) we intercept the original ShellCollector, forward all messages and
        //    emit a message to a "heartbeat" channel every time we see output coming in
        // 2) at the same time, we run a concurrent coroutine, the "heartbeat detector" that
        //    checks the "heartbeat" channel for messages. If there is no incoming message
        //    for longer than the timeout, the coroutine throws an `TimeoutException`, which
        //    cancels the whole flow with a `TimeoutException`.
        val heartbeatChannel = Channel<Unit>()
        val forwardingCollector = createForwardingCollector(host, heartbeatChannel, parameters.shellCollector)
        val heartbeatDetector = HeartbeatDetector(host, heartbeatChannel, parameters.commandOutputTimeout)
        coroutineScope {
            // Launch our command (in)activity detector
            launch {
                heartbeatDetector.run()
            }

            // Start our regular shell command execution
            val shellFlow = execute(parameters, forwardingCollector)

            // Forward from the regular shell flow to the returned flow
            shellFlow.collect {
                emit(it)
            }

            // Ensure the idle detector exits its loop
            heartbeatChannel.close()
        }
    }.flowOn(host.ioDispatcher)

    abstract fun createForwardingCollector(
        host: AdbLibHost,
        heartbeatChannel: SendChannel<Unit>,
        delegate: TShellCollector
    ): TShellCollector

    abstract fun execute(
        parameters: Parameters<TShellCollector>,
        forwardingCollector: TShellCollector
    ): Flow<T>

    /**
     * A [ShellCollector] that forwards all messages to another [ShellCollector]
     * while at the same time emits "heartbeats" on a [SendChannel] when messages
     * are forwarded.
     */
    protected class ForwardingShellCollector<T>(
        host: AdbLibHost,
        private val heartbeatChannel: SendChannel<Unit>,
        private val delegate: ShellCollector<T>
    ) : ShellCollector<T> {

        private val logger = thisLogger(host)

        override suspend fun start(collector: FlowCollector<T>) {
            logger.verbose { "start" }
            heartbeatChannel.send(Unit)
            delegate.start(collector)
        }

        override suspend fun collect(collector: FlowCollector<T>, stdout: ByteBuffer) {
            logger.verbose { "collect" }
            heartbeatChannel.send(Unit)
            delegate.collect(collector, stdout)
        }

        override suspend fun end(collector: FlowCollector<T>) {
            logger.verbose { "end" }
            heartbeatChannel.send(Unit)
            delegate.end(collector)
        }
    }

    /**
     * A [ShellV2Collector] that forwards all messages to another [ShellV2Collector]
     * while at the same time emits "heartbeats" on a [SendChannel] when messages
     * are forwarded.
     */
    protected class ForwardingShellV2Collector<T>(
        host: AdbLibHost,
        private val heartbeatChannel: SendChannel<Unit>,
        private val delegate: ShellV2Collector<T>
    ) : ShellV2Collector<T> {

        private val logger = thisLogger(host)

        override suspend fun start(collector: FlowCollector<T>) {
            logger.verbose { "start" }
            heartbeatChannel.send(Unit)
            delegate.start(collector)
        }

        override suspend fun collectStdout(collector: FlowCollector<T>, stdout: ByteBuffer) {
            logger.verbose { "collect" }
            heartbeatChannel.send(Unit)
            delegate.collectStdout(collector, stdout)
        }

        override suspend fun collectStderr(collector: FlowCollector<T>, stderr: ByteBuffer) {
            logger.verbose { "collect" }
            heartbeatChannel.send(Unit)
            delegate.collectStderr(collector, stderr)
        }

        override suspend fun end(collector: FlowCollector<T>, exitCode: Int) {
            logger.verbose { "end" }
            heartbeatChannel.send(Unit)
            delegate.end(collector, exitCode)
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

internal class ShellV2WithIdleMonitoring<T>(
    parameters: Parameters<ShellV2Collector<T>>
) : ShellWithIdleMonitoring<T, ShellV2Collector<T>>(parameters) {

    override fun createForwardingCollector(
        host: AdbLibHost,
        heartbeatChannel: SendChannel<Unit>,
        delegate: ShellV2Collector<T>
    ): ShellV2Collector<T> {
        return ForwardingShellV2Collector(host, heartbeatChannel, delegate)
    }

    override fun execute(
        parameters: Parameters<ShellV2Collector<T>>,
        forwardingCollector: ShellV2Collector<T>
    ): Flow<T> {
        return parameters.deviceServices.shellV2(
            parameters.device,
            parameters.command,
            forwardingCollector,
            parameters.stdinChannel,
            parameters.commandTimeout,
            parameters.bufferSize
        )
    }
}

internal class LegacyExecWithIdleMonitoring<T>(
    parameters: Parameters<ShellCollector<T>>
) : ShellWithIdleMonitoring<T, ShellCollector<T>>(parameters) {

    override fun createForwardingCollector(
        host: AdbLibHost,
        heartbeatChannel: SendChannel<Unit>,
        delegate: ShellCollector<T>
    ): ShellCollector<T> {
        return ForwardingShellCollector(host, heartbeatChannel, delegate)
    }

    override fun execute(
        parameters: Parameters<ShellCollector<T>>,
        forwardingCollector: ShellCollector<T>
    ): Flow<T> {
        return parameters.deviceServices.exec(
            parameters.device,
            parameters.command,
            forwardingCollector,
            parameters.stdinChannel,
            parameters.commandTimeout,
            parameters.bufferSize
        )
    }
}

internal class LegacyShellWithIdleMonitoring<T>(
    parameters: Parameters<ShellCollector<T>>
) : ShellWithIdleMonitoring<T, ShellCollector<T>>(parameters) {

    override fun createForwardingCollector(
        host: AdbLibHost,
        heartbeatChannel: SendChannel<Unit>,
        delegate: ShellCollector<T>
    ): ShellCollector<T> {
        return ForwardingShellCollector(host, heartbeatChannel, delegate)
    }

    override fun execute(
        parameters: Parameters<ShellCollector<T>>,
        forwardingCollector: ShellCollector<T>
    ): Flow<T> {
        return parameters.deviceServices.shell(
            parameters.device,
            parameters.command,
            forwardingCollector,
            parameters.stdinChannel,
            parameters.commandTimeout,
            parameters.bufferSize
        )
    }
}
