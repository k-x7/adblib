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
import com.android.adblib.impl.channels.AdbInputChannelReader
import com.android.adblib.impl.channels.AdbChannelReaderToReceiveChannel
import com.android.adblib.utils.AdbProtocolUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.charset.Charset
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
    suspend fun read(
        buffer: ByteBuffer,
        timeout: Long = Long.MAX_VALUE,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ): Int

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
    suspend fun readExactly(
        buffer: ByteBuffer,
        timeout: Long = Long.MAX_VALUE,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ) {
        val tracker = TimeoutTracker.fromTimeout(unit, timeout)
        tracker.throwIfElapsed()

        // This default implementation is suboptimal and can be optimized by implementers
        while (buffer.hasRemaining()) {
            val count = read(buffer, tracker)
            if (count == -1) {
                throw EOFException("Unexpected end of channel")
            }
        }
    }
}

internal suspend inline fun AdbInputChannel.read(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
    return read(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
}

internal suspend inline fun AdbInputChannel.readExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
    readExactly(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
}

/**
 * Converts this [AdbInputChannel] to an [AdbChannelReader] for text reading operations.
 * The returned [AdbChannelReader] should be [closed][AutoCloseable] when done to ensure the
 * [AdbInputChannel] is [closed][AutoCloseable].
 */
fun AdbInputChannel.toChannelReader(
    charset: Charset = AdbProtocolUtils.ADB_CHARSET,
    newLine: String = AdbProtocolUtils.ADB_NEW_LINE,
    bufferCapacity: Int = 256
): AdbChannelReader {
    return AdbInputChannelReader(this, charset, newLine, bufferCapacity)
}

/**
 * Reads the contents of this [AdbInputChannel] in a concurrent coroutine of [scope]
 * and returns a [ReceiveChannel] of [String] for each line of text as lines are decoded
 * using the provided [charset].
 *
 * When the [AdbInputChannel] is fully read, the returned [ReceiveChannel] is closed without
 * a [cause][Throwable].
 *
 * If there is an exception reading or decoding the contents of [AdbInputChannel], the returned
 * [ReceiveChannel] is closed with the corresponding [Throwable] as the cause.
 */
fun AdbInputChannel.readLines(
    scope: CoroutineScope,
    charset: Charset = AdbProtocolUtils.ADB_CHARSET,
    newLine: String = AdbProtocolUtils.ADB_NEW_LINE,
    bufferCapacity: Int = 256
): ReceiveChannel<String> {
    val reader = toChannelReader(charset, newLine, bufferCapacity)
    return AdbChannelReaderToReceiveChannel(scope, reader).start()
}
