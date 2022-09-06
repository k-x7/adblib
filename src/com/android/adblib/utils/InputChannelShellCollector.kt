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
package com.android.adblib.utils

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.ShellV2Collector
import com.android.adblib.impl.channels.DEFAULT_CHANNEL_BUFFER_SIZE
import com.android.adblib.shellCommand
import com.android.adblib.thisLogger
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * A [ShellV2Collector] that exposes the output of a [shellCommand] as a [InputChannelShellOutput],
 * itself exposing `stdout`, `stderr` as [AdbInputChannel] instances.
 */
class InputChannelShellCollector(
    session: AdbSession,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) : ShellV2Collector<InputChannelShellOutput> {

    private val logger = thisLogger(session)

    private val shellOutput = InputChannelShellOutputImpl(session, bufferSize)

    override suspend fun start(collector: FlowCollector<InputChannelShellOutput>) {
        collector.emit(shellOutput)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<InputChannelShellOutput>,
        stdout: ByteBuffer
    ) {
        while (stdout.remaining() > 0) {
            logger.verbose { "collectStdout(${stdout.remaining()})" }
            shellOutput.writeStdout(stdout)
        }
        logger.verbose { "collectStdout: done" }
    }

    override suspend fun collectStderr(
        collector: FlowCollector<InputChannelShellOutput>,
        stderr: ByteBuffer
    ) {
        while (stderr.remaining() > 0) {
            logger.verbose { "collectStderr(${stderr.remaining()})" }
            shellOutput.writeStderr(stderr)
        }
        logger.verbose { "collectStderr: done" }
    }

    override suspend fun end(collector: FlowCollector<InputChannelShellOutput>, exitCode: Int) {
        logger.verbose { "end(exitCode=$exitCode)" }
        shellOutput.end(exitCode)
    }
}

/**
 * The [shellCommand] output when using the [InputChannelShellCollector] collector.
 */
interface InputChannelShellOutput {

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    val stdout: AdbInputChannel

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    val stderr: AdbInputChannel

    /**
     * A [StateFlow] for the exit code of the shell command.
     * * While the command is still running, the value is `null`.
     * * Once the command terminates, the value is set to the actual
     *   (and final) exit code.
     */
    val exitCode: StateFlow<Int?>
}

internal class InputChannelShellOutputImpl(
    val session: AdbSession,
    bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
) :  InputChannelShellOutput, AutoCloseable {

    private val exitCodeFlow = MutableStateFlow<Int?>(null)

    private val stdoutChannel = session.channelFactory.createPipedChannel(bufferSize)

    private val stdoutOutputPipe
        get() = stdoutChannel.pipeSource

    private val stderrChannel = session.channelFactory.createPipedChannel(bufferSize)

    private val stderrOutputPipe
        get() = stderrChannel.pipeSource

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    override val stdout: AdbInputChannel
        get() = stdoutChannel

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    override val stderr: AdbInputChannel
        get() = stderrChannel

    /**
     * A [StateFlow] for the exit code of the shell command.
     * * While the command is still running, the value is `-1`.
     * * Once the command terminates, the value is set to the actual
     *   (and final) exit code.
     */
    override val exitCode: StateFlow<Int?> = exitCodeFlow.asStateFlow()

    suspend fun writeStdout(stdout: ByteBuffer) {
        stdoutOutputPipe.write(stdout)
    }

    suspend fun writeStderr(stderr: ByteBuffer) {
        stderrOutputPipe.write(stderr)
    }

    suspend fun end(exitCode: Int) {
        stdoutOutputPipe.close()
        stderrOutputPipe.close()
        exitCodeFlow.emit(exitCode)
    }

    override fun close() {
        stdoutOutputPipe.close()
        stderrOutputPipe.close()
    }
}
