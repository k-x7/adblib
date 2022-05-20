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

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

private const val MAX_CHAR_SEQUENCE_BYTES = 10

/**
 * A wrapper around a [CharsetDecoder] to decode a sequence of bytes received from
 * an ADB device.
 *
 * Any unmappable character (or malformed sequence) results in a "?" character in the stream.
 */
class AdbBufferDecoder(bufferCapacity: Int = 256, charset: Charset = AdbProtocolUtils.ADB_CHARSET) {

    /**
     * The decoder used across calls to [decodeBuffer]
     */
    private val decoder: CharsetDecoder = charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
        .replaceWith("?")

    /**
     * The [CharBuffer] used to temporarily store decoded characters when calling
     * [CharsetDecoder.decode]
     */
    private val charBuffer: CharBuffer = CharBuffer.allocate(bufferCapacity)

    /**
     * (Optional) [ByteBuffer] used to store leftover bytes from the previous [decodeBuffer] call,
     * in case a multi-byte character sequence was not fully terminated at the end of the
     * processed buffer, e.g. we are processing an UTF-8 character of 3 bytes but only
     * 1 or 2 were present at the end of the [ByteBuffer] passed to [decodeBuffer].
     *
     * Note: Once this buffer is allocated, it is kept around in case it is needed again later on.
     */
    private var leftoverBuffer: ByteBuffer? = null

    /**
     * Decode characters in a [ByteBuffer], invoking [processor] when a valid character sequence
     * has been decoded.
     */
    fun decodeBuffer(buffer: ByteBuffer, processor: (CharBuffer) -> Unit) {
        leftoverBuffer?.apply {
            decodeLeftoverBuffer(this, buffer, processor)
        }

        while (true) {
            val result = decoder.decode(buffer, charBuffer, false)
            when {
                result.isMalformed -> {
                    // Skip (the malformed sequence has been replaced with "?")
                    continue
                }

                result.isUnmappable -> {
                    // Skip (the incorrect sequence has been replaced with "?")
                    continue
                }

                // CharBuffer is too small, append contents to StringBuilder and continue
                result.isOverflow -> {
                    flushCharBuffer(processor)
                    continue
                }

                // ByteBuffer has been processed as far as possible,
                // append contents to StringBuilder and exit the loop
                result.isUnderflow -> {
                    flushCharBuffer(processor)
                    // If there are any remaining bytes, store a copy for the next call.
                    // This happens when the end of the buffer contains only a partial
                    // multi-byte character sequence.
                    if (buffer.hasRemaining()) {
                        leftoverBuffer = ByteBuffer.allocate(MAX_CHAR_SEQUENCE_BYTES)
                        leftoverBuffer?.put(buffer)
                        assert(!buffer.hasRemaining())
                    }
                    break
                }

                else -> {
                    // This should never happen
                    throw IllegalStateException()
                }
            }
        }
    }

    private fun decodeLeftoverBuffer(
        leftovers: ByteBuffer,
        buffer: ByteBuffer,
        processor: (CharBuffer) -> Unit
    ) {
        if (leftovers.position() == 0) {
            return
        }

        // Flush char buffer now to make sure we have room in it (we need only
        // one character, but we may be unlucky)
        flushCharBuffer(processor)

        // We transfer one byte at a time from [buffer] to [leftoverBuffer] until
        // we can decode one character (or [buffer] is exhausted again).
        // When complete, [buffer] position is adjusted so that its position points
        // to the beginning of the next character (or end of buffer).
        while (buffer.hasRemaining()) {
            // Transfer one byte
            //
            // Note: We assume we always have enough room in the leftover buffer, because we
            //       allocate it with a size larger than the max. sequence of bytes
            //       of a character (we need to store only one character)
            leftovers.put(buffer.get())
            val count = leftovers.position() // so we can reset position if needed
            leftovers.flip()

            // Try decoding with the additional byte
            val result = decoder.decode(leftovers, charBuffer, false)
            val done = when {
                // invalid UTF-8 mapped to "?"
                result.isMalformed -> true

                // invalid UTF-8 mapped to "?"
                result.isUnmappable -> true

                // char buffer has no room, this should never happen
                result.isOverflow -> throw IllegalStateException()

                // We are done only if the decoder processed all leftover characters
                result.isUnderflow -> !leftovers.hasRemaining()

                // This should never happen
                else -> throw IllegalStateException()
            }
            if (done) {
                assert(!leftovers.hasRemaining())
                leftovers.clear()
                break
            }
            leftovers.clear()
            leftovers.position(count)
        }
    }

    private fun flushCharBuffer(processor: (CharBuffer) -> Unit) {
        if (charBuffer.position() > 0) {
            charBuffer.flip()
            processor(charBuffer)
        }
        charBuffer.clear()
    }
}
