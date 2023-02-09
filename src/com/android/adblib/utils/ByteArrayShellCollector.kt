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

import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.utils.ByteArrayShellCollector.CommandResult
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * A [ShellCollector] implementation that concatenates the entire `stdout` into a single
 * [ByteArray].
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class ByteArrayShellCollector : ShellV2Collector<CommandResult> {
    private val decoder = AdbBufferDecoder()

    private val stdoutBuffer = ResizableBuffer()
    private val stderrText = StringBuilder()

    override suspend fun start(collector: FlowCollector<CommandResult>) {}

    override suspend fun collectStdout(
        collector: FlowCollector<CommandResult>,
        stdout: ByteBuffer
    ) {
        stdoutBuffer.appendBytes(stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<CommandResult>,
        stderr: ByteBuffer
    ) {
        decoder.decodeBuffer(stderr) { stderrText.append(it) }
    }

    override suspend fun end(collector: FlowCollector<CommandResult>, exitCode: Int) {
        val writeBuffer = stdoutBuffer.forChannelWrite()
        val array = ByteArray(writeBuffer.limit())
        writeBuffer.get(array)
        collector.emit(CommandResult(array, stderrText.toString(), exitCode))
    }

    class CommandResult(val stdout: ByteArray, val stderr: String, val exitCode: Int)
}
