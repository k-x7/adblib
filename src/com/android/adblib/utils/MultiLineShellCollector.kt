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
import com.android.adblib.utils.AdbProtocolUtils.ADB_NEW_LINE
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer
import java.nio.CharBuffer

/**
 * A [ShellCollector] implementation that collects `stdout` as a sequence of lines
 */
class MultiLineShellCollector(bufferCapacity: Int = 256) : ShellCollector<String> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    /**
     * Store an unfinished line from the previous call to [collect]
     */
    private var previousString = StringBuilder()

    /**
     * Lines accumulated during a single call to [collectLines]
     */
    private val lines = ArrayList<String>()

    /**
     * We store the lambda in a field to avoid allocating an new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val lineCollector = this::collectLines

    override suspend fun start(collector: FlowCollector<String>, transportId: Long?) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<String>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, lineCollector)
        emitLines(collector)
    }

    override suspend fun end(collector: FlowCollector<String>) {
        collector.emit(previousString.toString())
    }

    private fun collectLines(charBuffer: CharBuffer) {
        var currentOffset = 0
        while (currentOffset < charBuffer.length) {
            val index = charBuffer.indexOf(ADB_NEW_LINE, currentOffset)
            if (index < 0) {
                previousString.append(charBuffer.substring(currentOffset))
                break
            }
            previousString.append(charBuffer.substring(currentOffset, index))
            lines.add(previousString.toString())
            previousString.clear()
            currentOffset = index + ADB_NEW_LINE.length
        }
    }

    private suspend fun emitLines(collector: FlowCollector<String>) {
        if (lines.isNotEmpty()) {
            for (line in lines) {
                collector.emit(line)
            }
            lines.clear()
        }
    }
}
