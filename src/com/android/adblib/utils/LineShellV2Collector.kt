/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.ShellCommandOutputElement.StderrLine
import com.android.adblib.ShellCommandOutputElement.StdoutLine
import com.android.adblib.ShellV2Collector
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class LineShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<ShellCommandOutputElement> {

    private val stdoutCollector = LineShellCollector(bufferCapacity)
    private val stderrCollector = LineShellCollector(bufferCapacity)
    private val stdoutFlowCollector = LineFlowCollector { line -> StdoutLine(line) }
    private val stderrFlowCollector = LineFlowCollector { line -> StderrLine(line) }

    override suspend fun start(collector: FlowCollector<ShellCommandOutputElement>) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<ShellCommandOutputElement>,
        stdout: ByteBuffer
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<ShellCommandOutputElement>,
        stderr: ByteBuffer
    ) {
        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(collector: FlowCollector<ShellCommandOutputElement>, exitCode: Int) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.end(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.end(stderrFlowCollector)

        collector.emit(ShellCommandOutputElement.ExitCode(exitCode))
    }

    class LineFlowCollector(
        private val builder: (String) -> ShellCommandOutputElement
    ) : FlowCollector<String> {

        var forwardingFlowCollector: FlowCollector<ShellCommandOutputElement>? = null

        override suspend fun emit(value: String) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}
