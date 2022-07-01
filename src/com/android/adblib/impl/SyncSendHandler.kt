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
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.SyncProgress
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.thisLogger
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.withPrefix
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.nio.file.attribute.FileTime

/**
 * Implementation of the `SEND` protocol of the `SYNC` command
 *
 * See [https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SYNC.TXT]
 */
internal class SyncSendHandler(
    private val serviceRunner: AdbServiceRunner,
    device: DeviceSelector,
    private val deviceChannel: AdbChannel
) {

    private val logger = thisLogger(host).withPrefix("device:$device,sync:SEND - ")

    private val host: AdbSessionHost
        get() = serviceRunner.host

    private val workBuffer = serviceRunner.newResizableBuffer().order(ByteOrder.LITTLE_ENDIAN)

    /**
     * From [SYNC.TXT](https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/SYNC.TXT;l=50)
     *
     * ```
     * SEND:
     * The remote file name is split into two parts separated by the last
     * comma (","). The first part is the actual path, while the second is a decimal
     * encoded file mode containing the permissions of the file on device.
     *
     * Note that some file types will be deleted before the copying starts, and if
     * the transfer fails. Some file types will not be deleted, which allows
     * adb push disk_image /some_block_device to work.
     *
     * After this the actual file is sent in chunks. Each chunk has the following format.
     * A sync request with id "DATA" and length equal to the chunk size. After
     * follows chunk size number of bytes. This is repeated until the file is
     * transferred. Each chunk must not be larger than 64k.
     *
     * When the file is transferred a sync request "DONE" is sent, where length is set
     * to the last modified time for the file. The server responds to this last
     * request (but not to chunk requests) with an "OKAY" sync response (length can
     * be ignored).
     * ```
     *
     * Note: This is not documented in the document above, but a failure is reported as
     * a "FAIL" chunk, length is the error message size, followed by the error message encoded
     * as a UTF-8 string. This can happen if there is an I/O error creating the file on the
     * remote device.
     *
     * If [remoteFileTime] is not provided, it defaults to the current system time.
     */
    suspend fun send(
        sourceChannel: AdbInputChannel,
        remoteFilePath: String,
        remoteFileMode: RemoteFileMode,
        remoteFileTime: FileTime?,
        progress: SyncProgress?,
        bufferSize: Int,
    ) {
        withContext(host.ioDispatcher) {
            // Note: ADB daemon implementation
            //       See [https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/daemon/file_sync_service.cpp;l=498;drc=fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f;bpv=0;bpt=1]
            logger.info { "$sourceChannel -> \"$remoteFilePath\"" }

            if (remoteFilePath.length > REMOTE_PATH_MAX_LENGTH) {
                throw IllegalArgumentException("Remote paths are limited to $REMOTE_PATH_MAX_LENGTH characters")
            }
            val remoteFileEpoch =
                if (remoteFileTime == null) (host.timeProvider.nanoTime() / 1_000_000_000L).toInt()
                else AdbProtocolUtils.convertFileTimeToEpochSeconds(remoteFileTime)

            // Send the file using the "SEND" query
            startSendRequest(remoteFilePath, remoteFileMode, progress)

            // Send the contents of the file from the input stream
            val byteCount = sendFileContents(remoteFilePath, sourceChannel, bufferSize, progress)

            // Finish the file with the last modification date
            commitRemoteFile(remoteFilePath, remoteFileEpoch, progress, byteCount)

            // The `SEND` operation is acknowledged by either "OKAY" or "FAIL" (in "SYNC" format)
            // TODO: In case of a "FAIL" happening early in the transfer process (e.g. the ADB
            //       daemon could not create the directory for the file), reading "DONE"/"FAIL"
            //       reading should be done before sending the whole file contents to the device,
            //       since that content will essentially be ignored.
            serviceRunner.consumeSyncOkayFailResponse(
                deviceChannel,
                workBuffer,
                TimeoutTracker.INFINITE
            )
        }
    }

    private suspend fun startSendRequest(
        remoteFilePath: String,
        remoteFileMode: RemoteFileMode,
        progress: SyncProgress?
    ) {
        progress?.transferStarted(remoteFilePath)

        logger.debug { "Starting sync request to \"$remoteFilePath\"" }
        // Bytes 0-3: 'SEND'
        // Bytes 4-7: request size (little endian)
        // Bytes 8-xx: An utf-8 string with the remote file path followed by ','
        //             followed by the mode (bits) as a decimal string
        workBuffer.clear()
        workBuffer.appendString("SEND", AdbProtocolUtils.ADB_CHARSET)
        val lengthPos = workBuffer.position
        workBuffer.appendInt(0) // Set later
        workBuffer.appendString(
            "${remoteFilePath},${remoteFileMode.modeBits}",
            AdbProtocolUtils.ADB_CHARSET
        )
        workBuffer.setInt(lengthPos, workBuffer.position - 8)

        deviceChannel.writeExactly(workBuffer.forChannelWrite())
    }

    private suspend fun sendFileContents(
        remoteFilePath: String,
        sourceChannel: AdbInputChannel,
        bufferSize: Int,
        progress: SyncProgress?
    ): Long {
        var totalBytesSoFar = 0L
        while (true) {
            // Bytes 0-3: 'DATA'
            // Bytes 4-7: request size (little endian)
            // Bytes 8-xx: file bytes
            workBuffer.clear()
            workBuffer.appendString("DATA", AdbProtocolUtils.ADB_CHARSET)
            val lengthPosition = workBuffer.position
            workBuffer.appendInt(0) // Set later
            val headerLength = workBuffer.position
            val byteCount = sourceChannel.read(workBuffer.forChannelRead(bufferSize - headerLength))
            if (byteCount < 0) {
                // We reached EOF, we are done
                logger.debug { "Done reading bytes from source channel $sourceChannel" }
                break
            }

            // We have data from 0 to position(8+byteCount),/ Write them all to the output
            val writeBuffer = workBuffer.afterChannelRead(0)
            workBuffer.setInt(lengthPosition, byteCount)
            deviceChannel.writeExactly(writeBuffer)

            totalBytesSoFar += byteCount
            progress?.transferProgress(remoteFilePath, totalBytesSoFar)
        }

        logger.debug { "Done writing bytes to channel $deviceChannel ($totalBytesSoFar bytes written)" }
        return totalBytesSoFar
    }

    private suspend fun commitRemoteFile(
        remoteFilePath: String,
        remoteFileEpoch: Int,
        progress: SyncProgress?,
        byteCount: Long
    ) {
        logger.debug { "Committing remote file $remoteFilePath ($byteCount bytes)" }

        // Bytes 0-3: 'DONE'
        // Bytes 4-7: modified date (since epoch, in seconds)
        workBuffer.clear()
        workBuffer.appendString("DONE", AdbProtocolUtils.ADB_CHARSET)
        workBuffer.appendInt(remoteFileEpoch)
        deviceChannel.writeExactly(workBuffer.forChannelWrite())

        progress?.transferDone(remoteFilePath, byteCount)
    }
}
