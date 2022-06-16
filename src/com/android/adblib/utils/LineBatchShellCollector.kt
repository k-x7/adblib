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

import com.android.adblib.ShellCollector
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * A [ShellCollector] implementation that collects `stdout` as a sequence of lists of lines
 */
class LineBatchShellCollector(bufferCapacity: Int = 256) : ShellCollector<List<String>> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    private val lineCollector = LineCollector()

    /**
     * We store the lambda in a field to avoid allocating a new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val lineCollectorLambda: (CharBuffer) -> Unit = { lineCollector.collectLines(it) }

    override suspend fun start(collector: FlowCollector<List<String>>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<List<String>>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, lineCollectorLambda)
        val lines = lineCollector.getLines()
        if (lines.isNotEmpty()) {
            collector.emit(lines.toList())
            lineCollector.clear()
        }
    }

    override suspend fun end(collector: FlowCollector<List<String>>) {
        collector.emit(listOf(lineCollector.getLastLine()))
    }
}
