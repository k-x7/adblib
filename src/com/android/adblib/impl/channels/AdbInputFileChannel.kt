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
package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.thisLogger
import kotlinx.coroutines.CancellableContinuation
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Implementation of [AdbInputChannel] over a [AsynchronousFileChannel]
 */
internal class AdbInputFileChannel(
    private val host: AdbSessionHost,
    private val file: Path,
    private val fileChannel: AsynchronousFileChannel
) : AdbInputChannel {

    private val logger = thisLogger(host)

    private var filePosition = 0L

    private val channelReadHandler = object : ChannelReadHandler(host, fileChannel) {
        override val supportsTimeout: Boolean
            get() = false

        override fun asyncRead(
            buffer: ByteBuffer,
            timeout: Long,
            unit: TimeUnit,
            continuation: CancellableContinuation<Int>,
            completionHandler: ContinuationCompletionHandler<Int>
        ) {
            // Note: Timeout is handled by base class because [supportsTimeout] is false
            fileChannel.read(buffer, filePosition, continuation, completionHandler)
        }

        override fun asyncReadCompleted(byteCount: Int) {
            if (byteCount > 0) {
                filePosition += byteCount
            }
        }
    }

    override fun toString(): String {
        return "AdbInputFileChannel(\"$file\")"
    }

    @Throws(Exception::class)
    override fun close() {
        logger.debug { "closing input channel for \"$file\"" }
        fileChannel.close()
    }

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return channelReadHandler.read(buffer, timeout, unit)
    }

    override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        channelReadHandler.readExactly(buffer, timeout, unit)
    }
}
