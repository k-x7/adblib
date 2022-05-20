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

import com.android.adblib.AdbChannelReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

internal class AdbChannelReaderToReceiveChannel(
    private val scope: CoroutineScope,
    private val reader: AdbChannelReader,
) {

    private val channel = Channel<String>()

    fun start(): ReceiveChannel<String> {
        launchReadLines()
        return channel
    }

    private fun launchReadLines() {
        scope.launch {
            readLinesWorker()
        }.invokeOnCompletion { throwable ->
            // This handles cancellation (both from the parent scope and the launched coroutine)
            // as well as errors.
            channel.close(throwable)
            reader.close()
        }
    }

    private suspend fun readLinesWorker() {
        while (true) {
            val line = reader.readLine() ?: break
            channel.send(line)
        }
    }
}
