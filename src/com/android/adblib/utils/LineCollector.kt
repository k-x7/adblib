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

import java.nio.CharBuffer

/**
 * Accepts chunks of [CharBuffer] and splits them into lines. Partial lines at the end of a chunk
 * are saved to be used with the next chunk.
 */
internal class LineCollector(private val newLine: String = AdbProtocolUtils.ADB_NEW_LINE) {

    /**
     * Store an unfinished line from the previous call to [collectLines]
     */
    private var previousString = StringBuilder()

    /**
     * Lines accumulated during a single call to [collectLines]
     */
    private val lines = ArrayList<String>()

    fun collectLines(charBuffer: CharBuffer) {
        var currentOffset = 0
        while (currentOffset < charBuffer.length) {
            val index = charBuffer.indexOf(newLine, currentOffset)
            if (index < 0) {
                previousString.append(charBuffer.substring(currentOffset))
                break
            }
            previousString.append(charBuffer.substring(currentOffset, index))
            lines.add(previousString.toString())
            previousString.clear()
            currentOffset = index + newLine.length
        }
    }

    fun getLines(): List<String> = lines

    fun getLastLine(): String = previousString.toString()

    fun clear() {
        lines.clear()
    }
}
