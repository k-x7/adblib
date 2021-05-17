package com.android.adblib

import com.android.adblib.utils.TimeoutTracker
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

interface AdbOutputChannel : AutoCloseable {
    /**
     * Sends up to [ByteBuffer.remaining] bytes contained in the [buffer][ByteBuffer] parameter, i.e. some
     * bytes from the buffer [position][ByteBuffer.position] to the [limit][ByteBuffer.limit].
     *
     * Returns the number of bytes written on success. The return value is zero if and only if
     * [ByteBuffer.remaining()] is zero.
     *
     * If a failure occurs, an [java.io.IOException] is thrown, and the [ByteBuffer] state
     * is undefined (i.e. some bytes may have been written, but not all).
     *
     * Throws a [TimeoutException] in case the data cannot be written before the timeout expires.
     */
    suspend fun write(buffer: ByteBuffer, timeout: TimeoutTracker): Int

    /**
     * Sends all [ByteBuffer.remaining] bytes contained in the [buffer][ByteBuffer] parameter, i.e. all
     * bytes from the buffer [position][ByteBuffer.position] to the [limit][ByteBuffer.limit].
     *
     * If successful, the buffer position is equal to the buffer limit.
     *
     * If a failure occurs, an [java.io.IOException] is thrown, and the [ByteBuffer] state
     * is undefined (i.e. some bytes may have been written, but not all).
     *
     * Throws a [TimeoutException] in case the data cannot be written before the timeout expires.
     */
    suspend fun writeExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
        // This default implementation is sub-optimal and can be optimized by implementers
        while (buffer.hasRemaining()) {
            val count = write(buffer, timeout)
            if (count <= 0) {
                throw EOFException("Unexpected end of channel")
            }
        }
    }
}
