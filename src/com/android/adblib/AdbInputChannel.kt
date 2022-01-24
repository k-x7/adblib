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
    suspend fun read(buffer: ByteBuffer, timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.MILLISECONDS): Int

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
    suspend fun readExactly(buffer: ByteBuffer, timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        val tracker = TimeoutTracker(timeout, unit)
        tracker.throwIfElapsed()

        // This default implementation is sub-optimal and can be optimized by implementers
        while (buffer.hasRemaining()) {
            val count = read(buffer, tracker)
            if (count == -1) {
                throw EOFException("Unexpected end of channel")
            }
        }
    }
}

internal suspend fun AdbInputChannel.read(buffer: ByteBuffer, timeout: TimeoutTracker) : Int {
    return read(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
}

internal suspend fun AdbInputChannel.readExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
    readExactly(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
}
