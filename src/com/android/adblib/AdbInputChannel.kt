package com.android.adblib

import com.android.adblib.utils.TimeoutTracker
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException

interface AdbInputChannel : AutoCloseable {

    /**
     * Reads up to [ByteBuffer.remaining] bytes from the underlying channel.
     *
     * Returns the number of bytes read, or `-1` if end of stream is reached.
     * The returned value is never `0`, as this method returns only if there is at least 1 byte available.
     *
     * Throws an [java.io.IOException] in case of error, or a [TimeoutException]
     * in case no data is available before the timeout expires.
     */
    suspend fun read(buffer: ByteBuffer, timeout: TimeoutTracker): Int

    /**
     * Reads exactly [ByteBuffer.remaining] bytes from the underlying channel.
     *
     * On success, the buffer position is equal to the buffer limit.
     *
     * If end of stream is reached before all bytes are read, throws an [java.io.EOFException] and the
     * [ByteBuffer] state is undefined (i.e. some bytes may have been read, but not all)

     * Throws an [java.io.IOException] in case of error, or a [TimeoutException]
     * in case no data is available before the timeout expires.
     */
    suspend fun readExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
        // This default implementation is sub-optimal and can be optimized by implementers
        while (buffer.hasRemaining()) {
            val count = read(buffer, timeout)
            if (count == -1) {
                throw EOFException("Unexpected end of channel")
            }
        }
    }
}
