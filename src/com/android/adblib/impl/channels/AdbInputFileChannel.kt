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
import com.android.adblib.impl.TimeoutTracker
import kotlinx.coroutines.CancellableContinuation
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.Channel
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

    private val loggerPrefix = javaClass.simpleName

    private var filePosition = 0L

    override fun toString(): String {
        return "AdbInputFileChannel(\"$file\")"
    }

    @Throws(Exception::class)
    override fun close() {
        host.logger.debug { "$loggerPrefix: closing input channel for \"$file\"" }
        fileChannel.close()
    }

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        val count = ReadOperation(host, timeout, unit, fileChannel, buffer, filePosition).execute()
        if (count >= 0) {
            filePosition += count
        }
        return count
    }

    private class ReadOperation(
      host: AdbSessionHost,
      timeout: Long,
      unit: TimeUnit,
      private val fileChannel: AsynchronousFileChannel,
      private val buffer: ByteBuffer,
      private val filePosition: Long
    ) : AsynchronousChannelReadOperation(host, timeout, unit) {

        override val channel: Channel
            get() = fileChannel

        override fun readChannel(
            timeout: TimeoutTracker,
            continuation: CancellableContinuation<Int>
        ) {
            //TODO: Implement timeout
            fileChannel.read(buffer, filePosition, continuation, this)
        }
    }
}
