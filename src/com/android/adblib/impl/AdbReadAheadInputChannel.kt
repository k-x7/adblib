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
package com.android.adblib.impl

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.thisLogger
import com.android.adblib.utils.createChildScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Implementation of an [AdbInputChannel] that reads data from another [AdbInputChannel]
 * in a coroutine that runs concurrently with [read] operations, hence "reading ahead"
 * data from the input.
 */
internal class AdbReadAheadInputChannel(
    session: AdbSession,
    private val input: AdbInputChannel,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) : AdbInputChannel {

    private val logger = thisLogger(session)

    /**
     * The [AdbPipedInputChannelImpl] used to concurrently store read-ahead data as well as
     * provide data for [read] operations.
     */
    private val pipe = AdbPipedInputChannelImpl(session, bufferSize)

    /**
     * The scope of the read-ahead coroutine.
     */
    private val scope = session.scope.createChildScope(isSupervisor = true)

    init {
        scope.launch {
            kotlin.runCatching {
                readAhead(input, bufferSize)
            }.onFailure { exception: Throwable ->
                pipe.pipeSource.error(exception)
            }
        }
    }

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return pipe.read(buffer, timeout, unit).also {
            logger.verbose { "read: Read $it bytes from pipe '$pipe'" }
        }
    }

    override fun close() {
        logger.debug { "Closing read-ahead channel" }
        scope.cancel("Read ahead input closed")
        pipe.close()
        input.close()
    }

    private suspend fun readAhead(input: AdbInputChannel, bufferSize: Int) {
        logger.debug { "readAhead(input=$input, bufferSize=$bufferSize)" }
        val buffer = ByteBuffer.allocate(bufferSize)
        while (true) {
            buffer.clear() // [position=0, limit=capacity]
            val byteCount = input.read(buffer) // [position=byteCount, limit=capacity]
            logger.verbose { "readAhead: Read $byteCount bytes from input channel '$input'" }
            if (byteCount < 0) {
                // Reached EOF, signal "pipeSource" we are done adding bytes
                pipe.pipeSource.close()
                break
            }
            buffer.flip() // [position=byteCount, limit=capacity] -> [position=0, limit]
            pipe.pipeSource.writeExactly(buffer)
            logger.verbose { "readAhead: Written $byteCount bytes to pipe '$pipe'" }
        }
    }
}
