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
 * A [ShellCollector] implementation that concatenates the entire `stdout` into a single [String].
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class TextShellCollector(bufferCapacity: Int = 256) : ShellCollector<String> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    /**
     * Characters accumulated during calls to [collectCharacters]
     */
    private val stringBuilder = StringBuilder()

    /**
     * We store the lambda in a field to avoid allocating an new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val characterCollector = this::collectCharacters

    override suspend fun start(collector: FlowCollector<String>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<String>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, characterCollector)
    }

    override suspend fun end(collector: FlowCollector<String>) {
        collector.emit(stringBuilder.toString())
    }

    private fun collectCharacters(charBuffer: CharBuffer) {
        stringBuilder.append(charBuffer)
    }
}
