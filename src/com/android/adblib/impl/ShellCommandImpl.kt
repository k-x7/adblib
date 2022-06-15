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

import com.android.adblib.AdbFeatures
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibSession
import com.android.adblib.DEFAULT_SHELL_BUFFER_SIZE
import com.android.adblib.DeviceSelector
import com.android.adblib.INFINITE_DURATION
import com.android.adblib.ShellCommand
import com.android.adblib.ShellCommand.Protocol
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.availableFeatures
import com.android.adblib.deviceProperties
import com.android.adblib.impl.ShellWithIdleMonitoring.Parameters
import com.android.adblib.thisLogger
import com.android.adblib.utils.SuspendingLazy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import java.nio.ByteBuffer
import java.time.Duration

internal class ShellCommandImpl<T>(
    private val session: AdbLibSession,
    private val device: DeviceSelector,
    private val command: String,
) : ShellCommand<T> {

    private val logger = thisLogger(session)

    private var _allowLegacyShell: Boolean = true
    private var _allowLegacyExec: Boolean = true
    private var _allowShellV2: Boolean = true
    private var collector: ShellV2Collector<T>? = null
    private var commandTimeout: Duration = INFINITE_DURATION
    private var commandOutputTimeout: Duration? = null
    private var commandOverride: ((String, Protocol) -> String)? = null
    private var stdinChannel: AdbInputChannel? = null
    private var bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE

    override fun <U> withCollector(collector: ShellV2Collector<U>): ShellCommand<U> {
        @Suppress("UNCHECKED_CAST")
        val result = this as ShellCommandImpl<U>

        result.collector = collector
        return result
    }

    override fun <U> withLegacyCollector(collector: ShellCollector<U>): ShellCommand<U> {
        @Suppress("UNCHECKED_CAST")
        val result = this as ShellCommandImpl<U>

        result.collector = mapToShellV2Collector(collector)
        return result
    }

    override fun withStdin(stdinChannel: AdbInputChannel?): ShellCommand<T> {
        this.stdinChannel = stdinChannel
        return this
    }

    override fun withCommandTimeout(timeout: Duration): ShellCommand<T> {
        this.commandTimeout = timeout
        return this
    }

    override fun withCommandOutputTimeout(timeout: Duration): ShellCommand<T> {
        this.commandOutputTimeout = timeout
        return this
    }

    override fun withBufferSize(size: Int): ShellCommand<T> {
        this.bufferSize = size
        return this
    }

    override fun allowShellV2(value: Boolean): ShellCommand<T> {
        this._allowShellV2 = value
        return this
    }

    override fun allowLegacyExec(value: Boolean): ShellCommand<T> {
        this._allowLegacyExec = value
        return this
    }

    override fun allowLegacyShell(value: Boolean): ShellCommand<T> {
        this._allowLegacyShell = value
        return this
    }

    override fun withCommandOverride(commandOverride: (String, Protocol) -> String): ShellCommand<T> {
        this.commandOverride = commandOverride
        return this
    }

    override fun execute() = flow {
        shellFlow().collect {
            emit(it)
        }
    }

    private suspend fun shellFlow(): Flow<T> {
        val collector = collector ?: throw IllegalArgumentException("Collector is not set")

        val protocol = pickProtocol()
        val commandOutputTimeout = this.commandOutputTimeout
        val command = commandOverride?.invoke(command, protocol) ?: command
        return if (commandOutputTimeout != null) {
            logger.debug { "Executing command with protocol=$protocol and commandOutputTimeout=$commandOutputTimeout: $command" }
            when (protocol) {
                Protocol.SHELL_V2 -> {
                    ShellV2WithIdleMonitoring(
                        Parameters(
                            deviceServices = session.deviceServices,
                            device = device,
                            command = command,
                            shellCollector = collector,
                            stdinChannel = stdinChannel,
                            commandTimeout = commandTimeout,
                            commandOutputTimeout = commandOutputTimeout,
                            bufferSize = bufferSize
                        )
                    ).createFlow()
                }
                Protocol.EXEC -> {
                    LegacyExecWithIdleMonitoring(
                        Parameters(
                            deviceServices = session.deviceServices,
                            device = device,
                            command = command,
                            shellCollector = mapToLegacyCollector(collector),
                            stdinChannel = stdinChannel,
                            commandTimeout = commandTimeout,
                            commandOutputTimeout = commandOutputTimeout,
                            bufferSize = bufferSize
                        )
                    ).createFlow()
                }
                Protocol.SHELL -> {
                    LegacyShellWithIdleMonitoring(
                        Parameters(
                            deviceServices = session.deviceServices,
                            device = device,
                            command = command,
                            shellCollector = mapToLegacyCollector(collector),
                            stdinChannel = stdinChannel,
                            commandTimeout = commandTimeout,
                            commandOutputTimeout = commandOutputTimeout,
                            bufferSize = bufferSize
                        )
                    ).createFlow()
                }
            }
        } else {
            logger.debug { "Executing command with protocol=$protocol: $command" }
            when (protocol) {
                Protocol.SHELL_V2 -> {
                    session.deviceServices.shellV2(
                        device = device,
                        command = command,
                        shellCollector = collector,
                        stdinChannel = stdinChannel,
                        commandTimeout = commandTimeout,
                        bufferSize = bufferSize
                    )
                }
                Protocol.EXEC -> {
                    session.deviceServices.exec(
                        device = device,
                        command = command,
                        shellCollector = mapToLegacyCollector(collector),
                        stdinChannel = stdinChannel,
                        commandTimeout = commandTimeout,
                        bufferSize = bufferSize
                    )
                }
                Protocol.SHELL -> {
                    session.deviceServices.shell(
                        device = device,
                        command = command,
                        shellCollector = mapToLegacyCollector(collector),
                        stdinChannel = stdinChannel,
                        commandTimeout = commandTimeout,
                        bufferSize = bufferSize
                    )
                }
            }
        }
    }

    private suspend fun pickProtocol(): Protocol {
        val shellV2Supported = SuspendingLazy {
            // Shell V2 support is exposed as a device (and ADB feature).
            session.hostServices.availableFeatures(device).contains(AdbFeatures.SHELL_V2)
        }
        val execSupported = SuspendingLazy {
            // Exec support was added in API 21 (Lollipop)
            session.deviceServices.deviceProperties(device).api() >= 21
        }
        val protocol = when {
            _allowShellV2 && shellV2Supported.value() -> Protocol.SHELL_V2
            _allowLegacyExec && execSupported.value() -> Protocol.EXEC
            _allowLegacyShell -> Protocol.SHELL
            else -> throw IllegalArgumentException("No compatible shell protocol is supported or allowed")
        }
        return protocol
    }

    private fun <T> mapToLegacyCollector(shellV2Collector: ShellV2Collector<T>): ShellCollector<T> {
        return if (shellV2Collector is LegacyShellToShellV2Collector) {
            shellV2Collector.legacyShellCollector
        } else {
            ShellV2ToLegacyCollector(shellV2Collector)
        }
    }

    private fun <T> mapToShellV2Collector(shellCollector: ShellCollector<T>): ShellV2Collector<T> {
        return if (shellCollector is ShellV2ToLegacyCollector) {
            shellCollector.shellV2Collector
        } else {
            return LegacyShellToShellV2Collector(shellCollector)
        }
    }

    class LegacyShellToShellV2Collector<T>(
        internal val legacyShellCollector: ShellCollector<T>
    ) : ShellV2Collector<T> {

        override suspend fun start(collector: FlowCollector<T>) {
            legacyShellCollector.start(collector)
        }

        override suspend fun collectStdout(collector: FlowCollector<T>, stdout: ByteBuffer) {
            legacyShellCollector.collect(collector, stdout)
        }

        override suspend fun collectStderr(collector: FlowCollector<T>, stderr: ByteBuffer) {
            legacyShellCollector.collect(collector, stderr)
        }

        override suspend fun end(collector: FlowCollector<T>, exitCode: Int) {
            legacyShellCollector.end(collector)
        }
    }

    class ShellV2ToLegacyCollector<T>(
        internal val shellV2Collector: ShellV2Collector<T>
    ) : ShellCollector<T> {

        override suspend fun start(collector: FlowCollector<T>) {
            shellV2Collector.start(collector)
        }

        override suspend fun collect(collector: FlowCollector<T>, stdout: ByteBuffer) {
            shellV2Collector.collectStdout(collector, stdout)
        }

        override suspend fun end(collector: FlowCollector<T>) {
            shellV2Collector.end(collector, 0)
        }
    }
}
