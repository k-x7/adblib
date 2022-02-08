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
import com.android.adblib.DeviceSelector
import com.android.adblib.ProcessIdList
import com.android.adblib.impl.ProcessIdListParser
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.impl.TimeoutTracker.Companion.INFINITE
import com.android.adblib.thisLogger
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

/**
 * Starts and manages `track-jdwp` service invocations on devices.
 */
internal class TrackJdwpService(private val serviceRunner: AdbServiceRunner) {

    private val logger = thisLogger(host)

    private val parser = ProcessIdListParser()

    private val host
        get() = serviceRunner.host

    fun invoke(device: DeviceSelector, timeout: Long, unit: TimeUnit): Flow<ProcessIdList> = flow {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "track-jdwp"
        serviceRunner.runDaemonService(device, service, tracker) { channel, workBuffer ->
            collectAdbResponses(channel, workBuffer, service, this)
        }
    }.flowOn(host.ioDispatcher)

    private suspend fun collectAdbResponses(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        flowCollector: FlowCollector<ProcessIdList>
    ) {
        while (true) {
            // Note: We use an infinite timeout here, as the only way to end this request is to close
            //       the underlying ADB socket channel (or cancel the coroutine). This is by design.
            logger.debug { "\"${service}\" - waiting for next device tracking message" }
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, INFINITE)

            // Process list of process IDs and send it to the flow
            val processIdListString = AdbProtocolUtils.byteBufferToString(buffer)
            val processIdList = parser.parse(processIdListString)

            logger.debug { "\"${service}\" - sending list of (${processIdList.size} process ID(s))" }
            flowCollector.emit(processIdList)
        }
    }
}
