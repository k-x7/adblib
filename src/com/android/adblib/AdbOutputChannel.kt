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
package com.android.adblib

import com.android.adblib.impl.TimeoutTracker
import java.io.EOFException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
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
    suspend fun write(buffer: ByteBuffer, timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.MILLISECONDS): Int

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
    suspend fun writeExactly(buffer: ByteBuffer, timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        val tracker = TimeoutTracker(timeout, unit)
        tracker.throwIfElapsed()

        // This default implementation is sub-optimal and can be optimized by implementers
        while (buffer.hasRemaining()) {
            val count = write(buffer, tracker)
            if (count <= 0) {
                throw EOFException("Unexpected end of channel")
            }
        }
    }
}

internal suspend fun AdbOutputChannel.write(buffer: ByteBuffer, timeout: TimeoutTracker) : Int {
    return write(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
}

internal suspend fun AdbOutputChannel.writeExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
    writeExactly(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
}

/**
 * A [AdbOutputChannel] that requires calling [AutoShutdown.shutdown] to prevent data loss,
 * typically to allow flushing any pending writes to the underlying resource.
 */
interface AdbBufferedOutputChannel : AdbOutputChannel, AutoShutdown
