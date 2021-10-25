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
import com.android.adblib.ShellV2Collector
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class MultiLineShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<ShellCommandOutputElement> {

    private val stdoutCollector = MultiLineShellCollector(bufferCapacity)
    private val stderrCollector = MultiLineShellCollector(bufferCapacity)
    private val stdoutFlowCollector = MultiLineFlowCollector { line ->
        ShellCommandOutputElement.StdoutLine(line)
    }
    private val stderrFlowCollector = MultiLineFlowCollector { line ->
        ShellCommandOutputElement.StderrLine(line)
    }

    override suspend fun start(collector: FlowCollector<ShellCommandOutputElement>, transportId: Long?) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector, transportId)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector, transportId)
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

    class MultiLineFlowCollector(
        private val builder: (String) -> ShellCommandOutputElement
    ) : FlowCollector<String> {

        var forwardingFlowCollector: FlowCollector<ShellCommandOutputElement>? = null

        override suspend fun emit(value: String) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}