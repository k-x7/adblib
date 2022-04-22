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

import com.android.adblib.BatchShellCommandOutputElement
import com.android.adblib.BatchShellCommandOutputElement.ExitCode
import com.android.adblib.BatchShellCommandOutputElement.StderrLine
import com.android.adblib.BatchShellCommandOutputElement.StdoutLine
import com.android.adblib.ShellV2Collector
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class LineBatchShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<BatchShellCommandOutputElement> {

    private val stdoutCollector = LineBatchShellCollector(bufferCapacity)
    private val stderrCollector = LineBatchShellCollector(bufferCapacity)
    private val stdoutFlowCollector = LineBatchFlowCollector { lines -> StdoutLine(lines) }
    private val stderrFlowCollector = LineBatchFlowCollector { lines -> StderrLine(lines) }

    override suspend fun start(collector: FlowCollector<BatchShellCommandOutputElement>) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        stdout: ByteBuffer
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        stderr: ByteBuffer
    ) {
        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        exitCode: Int
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.end(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.end(stderrFlowCollector)

        collector.emit(ExitCode(exitCode))
    }

    class LineBatchFlowCollector(
        private val builder: (List<String>) -> BatchShellCommandOutputElement
    ) : FlowCollector<List<String>> {

        var forwardingFlowCollector: FlowCollector<BatchShellCommandOutputElement>? = null

        override suspend fun emit(value: List<String>) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}
