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
package com.android.adblib.impl.services

import com.android.adblib.AdbChannel
import com.android.adblib.AppProcessEntry
import com.android.adblib.DeviceSelector
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.impl.TimeoutTracker.Companion.INFINITE
import com.android.adblib.impl.AppProcessEntryListParser
import com.android.adblib.thisLogger
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

/**
 * Starts and manages `track-app` service invocations on devices.
 */
internal class TrackAppService(private val serviceRunner: AdbServiceRunner) {

    private val host
        get() = serviceRunner.host

    private val logger = thisLogger(host)

    private val parser = AppProcessEntryListParser()

    fun invoke(device: DeviceSelector, timeout: Long, unit: TimeUnit): Flow<List<AppProcessEntry>> =
        flow {
            val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
            val service = "track-app"
            serviceRunner.runDaemonService(device, service, tracker) { channel, workBuffer ->
                collectAdbResponses(channel, workBuffer, service, this)
            }
        }.flowOn(host.ioDispatcher)

    private suspend fun collectAdbResponses(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        flowCollector: FlowCollector<List<AppProcessEntry>>
    ) {
        while (true) {
            // Note: We use an infinite timeout here, as the only way to end this request is to close
            //       the underlying ADB socket channel (or cancel the coroutine). This is by design.
            logger.debug { "\"${service}\" - waiting for next device tracking message" }
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, INFINITE)

            // Process list of process IDs and send it to the flow
            val processEntryList = parser.parse(buffer)

            logger.debug { "\"${service}\" - sending list of (${processEntryList.size} process ID(s))" }
            logger.verbose { "\"${service}\" -    list elements: $processEntryList" }
            flowCollector.emit(processEntryList)
        }
    }
}
