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

import com.android.adblib.AdbLibHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.CancellableContinuation
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.Channel
import java.nio.file.Path

/**
 * Implementation of [AdbOutputChannel] over a [AsynchronousFileChannel]
 */
internal class AdbOutputFileChannel(
    private val host: AdbLibHost,
    private val file: Path,
    private val fileChannel: AsynchronousFileChannel
) : AdbOutputChannel {

    private val loggerPrefix = javaClass.simpleName

    private var filePosition = 0L

    override fun toString(): String {
        return "AdbOutputFileChannel(\"$file\")"
    }

    @Throws(Exception::class)
    override fun close() {
        host.logger.debug("$loggerPrefix: closing output channel for \"$file\"")
        fileChannel.close()
    }

    override suspend fun write(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
        val count = WriteOperation(host, timeout, fileChannel, buffer, filePosition).execute()
        if (count >= 0) {
            filePosition += count
        }
        return count
    }

    private class WriteOperation(
        host: AdbLibHost,
        timeout: TimeoutTracker,
        private val fileChannel: AsynchronousFileChannel,
        private val buffer: ByteBuffer,
        private val filePosition: Long
    ) : AsynchronousChannelWriteOperation(host, timeout) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override val channel: Channel
            get() = fileChannel

        override fun writeChannel(
            timeout: TimeoutTracker,
            continuation: CancellableContinuation<Int>
        ) {
            fileChannel.write(buffer, filePosition, continuation, this)
        }
    }
}