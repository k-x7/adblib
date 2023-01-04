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

import com.android.adblib.utils.AdbProtocolUtils
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

    /**
     * Consumes [charBuffer] characters (from [CharBuffer.position] to [CharBuffer.limit]), looking
     * for sequences of characters terminated by [newLine], and accumulating them as
     * [strings][String] in [lines] (see [getLines]).
     *
     * Note: This method consumes all characters of [charBuffer], [CharBuffer.limit] is equal
     * to [CharBuffer.position].
     */
    fun collectLines(charBuffer: CharBuffer) {
        // Look for newline characters within the [position, limit] range
        while (charBuffer.remaining() > 0) {
            val index = charBuffer.indexOf(newLine)
            if (index < 0) {
                // No newline in the remaining charBuffer: copy the remaining characters
                // to `previousString`, and move to the end of the buffer.
                previousString.append(charBuffer)
                charBuffer.position(charBuffer.limit())
                assert(charBuffer.remaining() == 0)
            } else {
                // There is a "newLine" as position "index": Copy characters from charBuffer
                // [position -> index] to `previousString`, then update charBuffer position
                // to be past the newline
                val savedLimit = charBuffer.limit()
                charBuffer.limit(charBuffer.position() + index)
                previousString.append(charBuffer)
                charBuffer.limit(savedLimit)
                charBuffer.position(charBuffer.position() + index + newLine.length)
                lines.add(previousString.toString())
                previousString.clear()
            }
        }
    }

    /**
     * Returns the list of lines [collected][collectLines] since the last call to [clear].
     */
    fun getLines(): List<String> = lines

    /**
     * Returns a non-empty string containing the last characters of the last call to [collectLines],
     * or the empty [String] if there were no leftover characters.
     */
    fun getLastLine(): String = previousString.toString()

    /**
     * Remove all lines from [lines], typically called after processing entries of [getLines].
     */
    fun clear() {
        lines.clear()
    }

}
