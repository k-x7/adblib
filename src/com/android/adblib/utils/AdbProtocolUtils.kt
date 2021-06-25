package com.android.adblib.utils

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CharsetDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.min

/**
 * Various low-level utility functions to deal with the ADB socket protocol
 */
object AdbProtocolUtils {
    val ADB_CHARSET: Charset = StandardCharsets.UTF_8
    const val ADB_NEW_LINE = "\n"

    fun isOkay(buffer: ByteBuffer): Boolean {
        return buffer[0] == 'O'.toByte() && buffer[1] == 'K'.toByte() && buffer[2] == 'A'.toByte() && buffer[3] == 'Y'.toByte()
    }

    fun isFail(buffer: ByteBuffer): Boolean {
        return buffer[0] == 'F'.toByte() && buffer[1] == 'A'.toByte() && buffer[2] == 'I'.toByte() && buffer[3] == 'L'.toByte()
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
        val maxCount = 16
        for (i in 0 until min(status.capacity(), maxCount)) {
            sb1.append(String.format("%02X", status[i]))
            sb2.append(String.format("%c", status[i].toChar()))
        }
        return String.format("0x%s (\"%s\")", sb1, sb2)
    }

    fun createDecoder(): CharsetDecoder {
        return ADB_CHARSET.newDecoder()
    }
}
