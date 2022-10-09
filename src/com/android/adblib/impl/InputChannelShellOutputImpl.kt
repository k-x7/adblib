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

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.InputChannelShellOutput
import com.android.adblib.impl.channels.DEFAULT_CHANNEL_BUFFER_SIZE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

internal class InputChannelShellOutputImpl(
    val session: AdbSession,
    bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
) : InputChannelShellOutput, AutoCloseable {

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
