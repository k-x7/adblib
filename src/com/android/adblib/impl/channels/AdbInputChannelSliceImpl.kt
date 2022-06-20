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
package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

internal class AdbInputChannelSliceImpl(
    private val inputChannel: AdbInputChannel,
    private val length: Int
) : AdbInputChannel {

    private var closed = false

    private var count: Int = 0

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        if (closed) {
            throw ClosedChannelException()
        }

        if (count >= length) {
            return -1
        }
        val remainingBytes = length - count

        // Use a slice if needed so that we don't read too much from underlying channel
        val slice = if (buffer.remaining() > remainingBytes) {
            buffer.slice().also {
                it.limit(it.position() + remainingBytes)
                assert(it.remaining() == remainingBytes)
            }
        } else {
            buffer
        }

        // Read from underlying channel and update position
        val readCount = inputChannel.read(slice, timeout, unit)
        if (readCount >= 0) {
            count += readCount
            if (slice !== buffer) {
                buffer.position(buffer.position() + readCount)
            }
            assert(count <= length)
        }
        return readCount
    }

    override fun close() {
        closed = true
    }
}
