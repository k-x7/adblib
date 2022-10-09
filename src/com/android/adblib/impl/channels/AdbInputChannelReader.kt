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
package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbChannelReader
import com.android.adblib.utils.AdbBufferDecoder
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.impl.LineCollector
import com.android.adblib.utils.ResizableBuffer
import java.nio.charset.Charset

internal class AdbInputChannelReader(
    private val inputChannel: AdbInputChannel,
    charset: Charset = AdbProtocolUtils.ADB_CHARSET,
    newLine: String = AdbProtocolUtils.ADB_NEW_LINE,
    bufferCapacity: Int = 256
) : AdbChannelReader() {

    private val lineCollector = LineCollector(newLine)
    private val decoder = AdbBufferDecoder(bufferCapacity, charset)
    private val workBuffer = ResizableBuffer(bufferCapacity)

    /**
     * The previously decoded lines, to be used for the next calls to [readLine]
     */
    private val previousLines = mutableListOf<String>()
    private var eof = false

    override suspend fun readLine(): String? {
        while (true) {
            // Flush previous lines first
            val line = previousLines.removeFirstOrNull()
            if (line != null) {
                return line
            }

            // We have reached EOF, bail out
            if (eof) {
                return null
            }

            // Read more data from underlying channel
            readChunk()
        }
    }

    private suspend fun readChunk() {
        if (eof) {
            return
        }

        workBuffer.clear()
        val byteCount = inputChannel.read(workBuffer.forChannelRead(workBuffer.capacity))
        if (byteCount < 0) {
            val lastLine = lineCollector.getLastLine()
            if (lastLine.isNotEmpty()) {
                previousLines.add(lastLine)
            }
            eof = true
            return
        }

        // Collect lines from collected chunk of bytes
        decoder.decodeBuffer(workBuffer.afterChannelRead()) { charBuffer ->
            lineCollector.collectLines(charBuffer)
            previousLines.addAll(lineCollector.getLines())
            lineCollector.clear()
        }
    }

    override fun close() {
        inputChannel.close()
    }
}
