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
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

private const val CARRIAGE_RETURN = '\r'.code.toByte()
private const val NEWLINE = '\n'.code.toByte()
private val REMAINING_CR = ByteArray(1).apply { set(0, CARRIAGE_RETURN) }
private val REMAINING_EMPTY = ByteArray(0)

/**
 * A [ShellCollector] implementation that concatenates the entire `stdout` into a single
 * [ByteArray].
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 *
 * @param removeCarriageReturns If true, replaces `\r\n` with `\n`. This is to be used for devices
 * with SDK<24 where `adb shell` replaces every `\n` with a `\r\n` even for binary content.
 */
class ByteArrayShellCollector(removeCarriageReturns: Boolean = false) : ShellCollector<ByteArray> {

    private val buffer = ResizableBuffer()
    private val carriageReturnRemover: CarriageReturnRemover? =
        if (removeCarriageReturns) CarriageReturnRemover() else null

    override suspend fun start(collector: FlowCollector<ByteArray>) {}

    override suspend fun collect(collector: FlowCollector<ByteArray>, stdout: ByteBuffer) {
        buffer.appendBytes(carriageReturnRemover?.removeCarriageReturns(stdout) ?: stdout)
    }

    override suspend fun end(collector: FlowCollector<ByteArray>) {
        carriageReturnRemover?.let { buffer.appendBytes(it.getRemaining()) }
        val writeBuffer = buffer.forChannelWrite()
        val array = ByteArray(writeBuffer.limit())
        writeBuffer.get(array)
        collector.emit(array)
    }
}

/**
 * Replaces `\r\n` with `\n`.
 */
private class CarriageReturnRemover {

    private val buffer = ResizableBuffer()

    private var hasLeftover = false

    /**
     * Replaces `\r\n` with `\n`.
     */
    fun removeCarriageReturns(input: ByteBuffer): ByteBuffer {
        // If there is no leftover CR we operate directly on the input buffer, otherwise, we use
        // a temporary buffer that we populate with an initial CR followed by the input buffer.
        val output = if (hasLeftover) {
            // If we have a leftover CR from the last buffer, we copy it into our work buffer and
            // then copy the input buffer on top of it.
            buffer.clear()
            buffer.forChannelRead(input.remaining() + 1).apply {
                put(CARRIAGE_RETURN)
                put(input)
                position(0)
            }
        } else {
            input
        }

        hasLeftover = input.get(input.limit() - 1) == CARRIAGE_RETURN
        output.removeCarriageReturns()
        return output
    }

    /**
     * Returns a ByteArray with a single CR byte if the last processed buffer ended with a CR.
     * Otherwise, returns an empty ByteArray.
     */
    fun getRemaining(): ByteArray = if (hasLeftover) REMAINING_CR else REMAINING_EMPTY
}

/**
 * Replaces `\r\n` with `\n`.
 *
 * The replacement is done inside the buffer by moving bytes backwards as needed.
 *
 * The position and mark of the buffer are unchanged but the limit may be adjusted to reflect the
 * smaller size.
 *
 * If the last byte of the buffer is a `\r`, we cannot check if the next byte is a `\n` so we don't
 * process it. We return true in this case so the `\r` can be added to the next buffer in read.
 */
private fun ByteBuffer.removeCarriageReturns() {
    val len = remaining()
    val start = position()
    var dstIndex = 0
    for (srcIndex in start until start + len) {
        val byte = get(srcIndex)
        if (byte == CARRIAGE_RETURN && (srcIndex == len - 1 || get(srcIndex + 1) == NEWLINE)) {
            continue
        }
        put(dstIndex++, byte)
    }
    limit(dstIndex)
}
