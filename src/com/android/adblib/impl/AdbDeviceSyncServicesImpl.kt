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
package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceSyncServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbOutputChannel
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.SyncProgress
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.withContext
import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

/**
 * Maximum length (in characters) of a remote path
 */
internal const val REMOTE_PATH_MAX_LENGTH = 1024

/**
 * Implementation of [AdbDeviceSyncServices]
 */
internal class AdbDeviceSyncServicesImpl private constructor(
    serviceRunner: AdbServiceRunner,
    device: DeviceSelector,
    private val deviceChannel: AdbChannel
) : AdbDeviceSyncServices {

    /**
     * Helper class to handle `SEND` commands
     */
    private val sendHandler = SyncSendHandler(serviceRunner, device, deviceChannel)

    /**
     * Helper class to handle `RECV` commands
     */
    private val recvHandler = SyncRecvHandler(serviceRunner, device, deviceChannel)

    override fun close() {
        deviceChannel.close()
    }

    override suspend fun send(
        sourceChannel: AdbInputChannel,
        remoteFilePath: String,
        remoteFileMode: RemoteFileMode,
        remoteFileTime: FileTime?,
        progress: SyncProgress?,
        bufferSize: Int
    ) {
        sendHandler.send(
            sourceChannel,
            remoteFilePath,
            remoteFileMode,
            remoteFileTime,
            progress,
            bufferSize
        )
    }

    override suspend fun recv(
        remoteFilePath: String,
        destinationChannel: AdbOutputChannel,
        progress: SyncProgress?,
        bufferSize: Int
    ) {
        recvHandler.recv(remoteFilePath, destinationChannel, progress)
    }

    companion object {

        /**
         * Returns a fully initialized instance of [AdbDeviceSyncServices], after successfully
         * starting a `SYNC` session with the ADB host.
         */
        suspend fun open(
            serviceRunner: AdbServiceRunner,
            device: DeviceSelector,
            timeout: Long,
            unit: TimeUnit
        ): AdbDeviceSyncServices {
            return withContext(serviceRunner.host.ioDispatcher) {
                val host = serviceRunner.host
                val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
                val workBuffer = serviceRunner.newResizableBuffer()
                // Switch the channel to the right transport (i.e. device)
                val channel = serviceRunner.switchToTransport(device, workBuffer, tracker)
                channel.closeOnException {
                    // Start the "sync" service
                    val localService = "sync:"
                    host.logger.debug { "$localService - sending local service request to ADB daemon, timeout: $tracker" }
                    serviceRunner.sendAdbServiceRequest(channel, workBuffer, localService, tracker)
                    serviceRunner.consumeOkayFailResponse(channel, workBuffer, tracker)

                    // Now that everything is setup, returns the instance
                    AdbDeviceSyncServicesImpl(serviceRunner, device, channel)
                }
            }
        }
    }
}
