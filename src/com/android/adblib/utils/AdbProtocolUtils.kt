package com.android.adblib.utils

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import kotlin.math.min

/**
 * Various low-level utility functions to deal with the ADB socket protocol
 */
object AdbProtocolUtils {

    /**
     * The ADB protocol uses UTF-8 encoding when strings are serialized on the communication
     * channel.
     */
    val ADB_CHARSET: Charset = StandardCharsets.UTF_8
    const val ADB_NEW_LINE = "\n"

    fun isOkay(buffer: ByteBuffer): Boolean {
        return is4Letters(buffer, "OKAY")
    }

    fun isFail(buffer: ByteBuffer): Boolean {
        return is4Letters(buffer, "FAIL")
    }

    fun isData(buffer: ByteBuffer): Boolean {
        return is4Letters(buffer, "DATA")
    }

    fun isDone(buffer: ByteBuffer): Boolean {
        return is4Letters(buffer, "DONE")
    }

    /**
     * Encode an integer value into a 4 byte integer representing the hexadecimal
     * string of that value.
     *
     * E.g. 1000 decimal -> "03E8" hex. string represented as integer ->
     */
    fun encodeLengthPrefix(length: Int): Int {
        if (length !in 0..0xFFFF) {
            throw IllegalArgumentException("ADB length values are limited to ${0..0xFFFF}")
        }

        return (digitToHexValue((length and 0xF000) shr 12) shl 24) +
               (digitToHexValue((length and 0x0F00) shr 8) shl 16) +
               (digitToHexValue((length and 0x00F0) shr 4) shl 8) +
               (digitToHexValue(length and 0x000F))
    }

    private fun digitToHexValue(digit: Int): Int {
        return when(digit) {
            in 0..9 -> '0'.code + digit
            else -> 'A'.code + digit - 10
        }
    }

    private fun is4Letters(buffer: ByteBuffer, letters: String): Boolean {
        if (buffer.remaining() < letters.length) {
            return false;
        }

        return buffer.get(0 + buffer.position()) == letters[0].code.toByte() &&
                buffer.get(1 + buffer.position()) == letters[1].code.toByte() &&
                buffer.get(2 + buffer.position()) == letters[2].code.toByte() &&
                buffer.get(3 + buffer.position()) == letters[3].code.toByte()
    }

    fun byteBufferToString(buffer: ByteBuffer): String {
        val position = buffer.position()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        buffer.position(position)
        return String(bytes, ADB_CHARSET)
    }

    fun bufferToByteDumpString(status: ByteBuffer): String {
        val sb1 = StringBuilder()
        val sb2 = StringBuilder()
        val maxCount = 24
        val position = status.position()
        for (i in 0 until min(status.remaining(), maxCount)) {
            val statusByte = status.get(i + position)
            sb1.append(String.format("%02x", statusByte))
            sb2.append(String.format("%c", statusByte.toChar()))
        }
        val overflow = if (status.remaining() > maxCount) " [truncated]" else ""
        return "$sb1 $sb2$overflow"
    }

    fun createDecoder(): CharsetDecoder {
        return ADB_CHARSET.newDecoder()
    }

    /**
     * Copy as many bytes as possible from [srcBuffer] to [dstBuffer]
     *
     * Returns the number of bytes copied, may be zero if [dstBuffer].[ByteBuffer.remaining]
     * or [srcBuffer].[ByteBuffer.remaining] is zero.
     */
    fun copyBufferContents(srcBuffer: ByteBuffer, dstBuffer: ByteBuffer): Int {
        return if (dstBuffer.remaining() > srcBuffer.remaining()) {
            // If dstBuffer has enough room, we can use a single operation
            val count = srcBuffer.remaining()
            dstBuffer.put(srcBuffer)
            count
        } else {
            // If dstBuffer is too small, we have to limit srcBuffer
            val count = dstBuffer.remaining()
            if (count > 0) {
                val savedLimit = srcBuffer.limit()
                srcBuffer.limit(srcBuffer.position() + count)
                dstBuffer.put(srcBuffer)
                srcBuffer.limit(savedLimit)
            }
            count
        }
    }

    fun convertFileTimeToEpochSeconds(fileTime: FileTime): Int {
        return (fileTime.toMillis() / 1_000).toInt()
    }
}
