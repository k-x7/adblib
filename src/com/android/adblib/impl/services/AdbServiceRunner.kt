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
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbSession
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.DeviceSelector
import com.android.adblib.createChannel
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.readExactly
import com.android.adblib.thisLogger
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.withOrder
import com.android.adblib.writeExactly
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val OKAY_FAIL_BYTE_COUNT = 4
private const val OKAY_FAIL_SYNC_BYTE_COUNT = 8
private const val LENGTH_PREFIX_BYTE_COUNT = 4

private const val TRANSPORT_ID_BYTE_COUNT = 8

/**
 * Helper class used to perform service requests with the ADB host
 */
internal class AdbServiceRunner(val session: AdbSession, private val channelProvider: AdbChannelProvider) {

    val host : AdbSessionHost
        get() = session.host

    private val logger = thisLogger(host)

    /**
     * Opens an [AdbChannel] and invokes a service on the ADB host, then waits for an OKAY/FAIL
     * response.
     *
     * In case of OKAY response, the returned [AdbChannel] is open and ready for the next steps
     * of the communication protocol.
     *
     * In case of FAIL response, an [AdbFailResponseException] exception is thrown. The exception
     * contains the error message included in the FAIL response.
     */
    suspend fun startHostQuery(
        workBuffer: ResizableBuffer,
        service: String,
        timeout: TimeoutTracker
    ): AdbChannel {
        val logPrefix = "Running ADB server query \"${service}\" -"
        logger.debug { "$logPrefix opening connection to ADB server, timeout=$timeout" }
        channelProvider.createChannel(timeout).closeOnException { channel ->
            logger.debug { "$logPrefix sending request to ADB server, timeout=$timeout" }
            sendAbdServiceRequest(channel, workBuffer, service, timeout)
            logger.debug { "$logPrefix receiving response from ADB server, timeout=$timeout" }
            consumeOkayFailResponse(channel, workBuffer, timeout)
            workBuffer.clear()
            return channel
        }
    }

    /**
     * Executes a query on the ADB host that relates to a single [device][DeviceSelector]
     */
    suspend fun runHostDeviceQuery(
        device: DeviceSelector,
        query: String,
        timeout: TimeoutTracker
    ): String {
        return runHostDeviceQuery(device, query, timeout, OkayDataExpectation.EXPECTED)
            ?: throw AdbProtocolErrorException("Data segment expected after OKAY response")
    }

    /**
     * Executes a query on the ADB host that relates to a single [device][DeviceSelector]
     */
    suspend fun runHostDeviceQuery(
        device: DeviceSelector,
        query: String,
        timeout: TimeoutTracker,
        okayData: OkayDataExpectation
    ): String? {
        val workBuffer = newResizableBuffer()
        val service = device.hostPrefix + ":" + query
        return startHostQuery(workBuffer, service, timeout).use { channel ->
            readOkayFailString(channel, workBuffer, service, timeout, okayData)
        }
    }

    /**
     * Executes a query on the ADB host that relates to a single [device][DeviceSelector].
     *
     * This method is similar to [runHostDeviceQuery], except the methods is for use with a
     * variation of the ADB protocol where the ADB Host sends back to 2 OKAY replies
     * (one for "connect", one for "status") instead of only one.
     */
    suspend fun runHostDeviceQuery2(
        device: DeviceSelector,
        query: String,
        timeout: TimeoutTracker,
        okayData: OkayDataExpectation
    ): String? {
        val workBuffer = newResizableBuffer()
        val service = device.hostPrefix + ":" + query
        return startHostQuery(workBuffer, service, timeout).use { channel ->
            // We receive 2 OKAY answers from the ADB Host: 1st OKAY is connect, 2nd OKAY is status.
            // See https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
            consumeOkayFailResponse(channel, workBuffer, timeout)
            readOkayFailString(channel, workBuffer, service, timeout, okayData)
        }
    }

    /**
     * Executes a `<host-prefix>` query and returns the data after the `OKAY` response
     * as a [String].
     */
    suspend fun runHostQuery(service: String, timeout: TimeoutTracker): String {
        return runHostQuery(service, timeout, OkayDataExpectation.EXPECTED)
            ?: throw AdbProtocolErrorException("Data segment expected after OKAY response")
    }

    /**
     * Executes a `<host-prefix>` query and returns the data after the `OKAY` response
     * as a [String].
     */
    suspend fun runHostQuery(
        service: String,
        timeout: TimeoutTracker,
        okayData: OkayDataExpectation
    ): String? {
        val workBuffer = newResizableBuffer()
        return startHostQuery(workBuffer, service, timeout).use { channel ->
            readOkayFailString(channel, workBuffer, service, timeout, okayData)
        }
    }

    private suspend fun readOkayFailString(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        timeout: TimeoutTracker,
        okayData: OkayDataExpectation
    ): String? {
        return when (okayData) {
            OkayDataExpectation.EXPECTED -> {
                val buffer = readLengthPrefixedData(channel, workBuffer, timeout)
                logger.debug { "\"${service}\" - read ${buffer.remaining()} byte(s), timeout=$timeout" }
                AdbProtocolUtils.byteBufferToString(buffer)
            }
            OkayDataExpectation.NOT_EXPECTED -> {
                null
            }
            OkayDataExpectation.OPTIONAL -> {
                try {
                    val buffer = readLengthPrefixedData(channel, workBuffer, timeout)
                    logger.debug { "\"${service}\" - read ${buffer.remaining()} byte(s), timeout=$timeout" }
                    AdbProtocolUtils.byteBufferToString(buffer)
                } catch (e: EOFException) {
                    null
                }
            }
        }
    }

    suspend fun sendAbdServiceRequest(
        channel: AdbOutputChannel,
        workBuffer: ResizableBuffer,
        service: String,
        timeout: TimeoutTracker
    ) {
        workBuffer.clear()
        workBuffer.appendInt(0) // Append 4 bytes we will fix later with the service length
        workBuffer.appendString(service, AdbProtocolUtils.ADB_CHARSET)

        // Fix up the service length
        val encodedServiceLength = workBuffer.position - 4
        workBuffer.setInt(0, AdbProtocolUtils.encodeLengthPrefix(encodedServiceLength))

        logger.debug { "\"${service}\" - sending service request string, timeout=$timeout" }
        return channel.writeExactly(workBuffer.forChannelWrite(), timeout)
    }

    /**
     * Executes a [query] on the ADB Daemon of the given [device][DeviceSelector]
     */
    suspend fun runDaemonQuery(
        device: DeviceSelector,
        query: String,
        timeout: TimeoutTracker
    ): String {
        return runDaemonQuery(device, query, timeout, OkayDataExpectation.EXPECTED)
            ?: throw AdbProtocolErrorException("Data segment expected after OKAY response")
    }

    /**
     * Executes a [query] on the ADB Daemon of the given [device][DeviceSelector]
     */
    suspend fun runDaemonQuery(
        device: DeviceSelector,
        query: String,
        timeout: TimeoutTracker,
        okayData: OkayDataExpectation
    ): String? {
        val workBuffer = newResizableBuffer()
        val channel = switchToTransport(device, workBuffer, timeout)
        return channel.use {
            sendAbdServiceRequest(channel, workBuffer, query, timeout)
            consumeOkayFailResponse(channel, workBuffer, timeout)
            readOkayFailString(channel, workBuffer, query, timeout, okayData)
        }
    }

    /**
     * Executes a [query] on the ADB Daemon of the given [device][DeviceSelector]
     */
    suspend fun runDaemonQuery2(
        device: DeviceSelector,
        query: String,
        timeout: TimeoutTracker,
        okayData: OkayDataExpectation
    ): String? {
        val workBuffer = newResizableBuffer()
        val channel = switchToTransport(device, workBuffer, timeout)
        return channel.use {
            sendAbdServiceRequest(channel, workBuffer, query, timeout)
            // We receive 2 OKAY answers from the ADB Host: 1st OKAY is connect, 2nd OKAY is status.
            // See https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1058
            consumeOkayFailResponse(channel, workBuffer, timeout)
            consumeOkayFailResponse(channel, workBuffer, timeout)
            readOkayFailString(channel, workBuffer, query, timeout, okayData)
        }
    }

    /**
     * Starts execution of a [service] on the ADB Daemon of the given [device][DeviceSelector],
     * and calls [block] when the ADB Daemon is ready to send/receive data corresponding
     * to the service invoked.
     */
    suspend fun runDaemonService(
        device: DeviceSelector,
        service: String,
        timeout: TimeoutTracker,
        block: suspend (AdbChannel, ResizableBuffer) -> Unit
    ) {
        val workBuffer = newResizableBuffer()
        startDaemonService(device, service, timeout).use { channel ->
            block(channel, workBuffer)
        }
    }

    /**
     * Starts execution of a [service] on the ADB Daemon of the given [device][DeviceSelector],
     * and returns a [AdbChannel] when the ADB Daemon is ready to send/receive data corresponding
     * to the service invoked.
     */
    suspend fun startDaemonService(
        device: DeviceSelector,
        service: String,
        timeout: TimeoutTracker,
        workBuffer: ResizableBuffer = newResizableBuffer()
    ): AdbChannel {
        val channel = switchToTransport(device, workBuffer, timeout)
        channel.closeOnException {
            logger.debug { "\"$service\" - sending local service request to ADB daemon, timeout: $timeout" }
            sendAbdServiceRequest(channel, workBuffer, service, timeout)
            consumeOkayFailResponse(channel, workBuffer, timeout)
        }
        return channel
    }

    /**
     * Reads OKAY or FAIL, with error message, from the channel.
     *
     * * If OKAY, returns the channel
     * * If FAIL, throws an [AdbProtocolErrorException] exception (with the error message)
     */
    suspend fun consumeOkayFailResponse(
        channel: AdbInputChannel,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ) {
        workBuffer.clear()
        channel.readExactly(workBuffer.forChannelRead(OKAY_FAIL_BYTE_COUNT), timeout)
        val data = workBuffer.afterChannelRead()
        logger.debug { "Read ${data.remaining()} bytes from channel: ${AdbProtocolUtils.bufferToByteDumpString(data)}" }
        assert(data.remaining() == OKAY_FAIL_BYTE_COUNT) { "readExactly() did not read the expected number of bytes" }

        when {
            AdbProtocolUtils.isOkay(data) -> {
                // Nothing to do
            }
            AdbProtocolUtils.isFail(data) -> {
                readFailResponseAndThrow(channel, workBuffer, timeout)
            }
            else -> {
                val error = AdbProtocolErrorException(
                    "Expected \"OKAY\" or \"FAIL\" response header, " +
                            "got \"${AdbProtocolUtils.bufferToByteDumpString(data)}\" instead"
                )
                throw error
            }
        }
    }

    /**
     * Reads `OKAY` or `FAIL` from a `sync protocol` connection channel
     *
     * * If `OKAY`, consume it and the following 4-byte length (which should always be zero)
     * * If `FAIL`, consume it and the following message, then throws an
     *   [AdbProtocolErrorException] exception (with the error message)
     */
    suspend fun consumeSyncOkayFailResponse(
        channel: AdbInputChannel,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ) {
        workBuffer.clear()
        workBuffer.order(ByteOrder.LITTLE_ENDIAN) // `Sync` protocol is always little endian
        channel.readExactly(workBuffer.forChannelRead(OKAY_FAIL_SYNC_BYTE_COUNT), timeout)
        val data = workBuffer.afterChannelRead()
        assert(data.remaining() == OKAY_FAIL_SYNC_BYTE_COUNT) { "readExactly() did not read the expected number of bytes" }

        when {
            // Bytes 0-3: 'OKAY'
            // Bytes 4-7: length (always 0)
            AdbProtocolUtils.isOkay(data) -> {
                // Nothing to do
            }

            // Bytes 0-3: 'FAIL'
            // Bytes 4-7: message length (little endian)
            // Bytes 8-xx: message bytes
            //
            // Note: This is not a "regular" `FAIL` message, as the length is a 4 byte little
            //       endian value, as opposed to 4 byte ascii string
            AdbProtocolUtils.isFail(data) -> {
                data.getInt() // Consume 'FAIL'
                val length = data.getInt() // Consume length (little endian)
                readSyncFailMessageAndThrow(channel, workBuffer, length, timeout)
            }
            else -> {
                val error = AdbProtocolErrorException(
                    "Expected \"OKAY\" or \"FAIL\" response header, " +
                            "got \"${AdbProtocolUtils.bufferToByteDumpString(data)}\" instead"
                )
                throw error
            }
        }
    }

    suspend fun readSyncFailMessageAndThrow(
        channel: AdbInputChannel,
        workBuffer: ResizableBuffer,
        length: Int,
        timeout: TimeoutTracker
    ) {
        workBuffer.clear()
        channel.readExactly(workBuffer.forChannelRead(length), timeout)
        val messageBuffer = workBuffer.afterChannelRead()
        throw AdbFailResponseException(messageBuffer)
    }

    private suspend fun readFailResponseAndThrow(
        channel: AdbInputChannel,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ) {
        val data = readLengthPrefixedData(channel, workBuffer, timeout)
        throw AdbFailResponseException(data)
    }

    /**
     * Reads length prefixed data from the [channel] into [workBuffer], as returns a [ByteBuffer]
     * that contains the data read.
     *
     * Note: The returned [ByteBuffer] is the result of calling [ResizableBuffer.afterChannelRead]
     * on [workBuffer], so the buffer is only valid as long as [workBuffer] is not changed.
     *
     */
    suspend fun readLengthPrefixedData(
        channel: AdbInputChannel,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ): ByteBuffer {
        workBuffer.clear()
        channel.readExactly(workBuffer.forChannelRead(LENGTH_PREFIX_BYTE_COUNT), timeout)
        val lengthBuffer = workBuffer.afterChannelRead()
        assert(lengthBuffer.remaining() == LENGTH_PREFIX_BYTE_COUNT) { "readExactly() did not read expected number of bytes" }
        val lengthString = AdbProtocolUtils.byteBufferToString(lengthBuffer)
        try {
            val length = lengthString.toInt(16)
            workBuffer.clear()
            channel.readExactly(workBuffer.forChannelRead(length), timeout)
            val data = workBuffer.afterChannelRead()
            logger.debug { "Read ${data.remaining()} bytes from channel: ${AdbProtocolUtils.bufferToByteDumpString(data)}" }
            return data
        } catch (e: NumberFormatException) {
            throw AdbProtocolErrorException(
                "Invalid format for ADB response length (\"$lengthString\")", e
            )
        }
    }

    fun newResizableBuffer(initialCapacity: Int = 256): ResizableBuffer {
        //TODO: Consider acquiring ResizableBuffer from a pool to allow re-using instances
        return ResizableBuffer(initialCapacity)
    }

    suspend fun switchToTransport(
        deviceSelector: DeviceSelector,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ): AdbChannel {
        val transportPrefix = deviceSelector.transportPrefix
        startHostQuery(workBuffer, transportPrefix, timeout).closeOnException { channel ->
            if (deviceSelector.responseContainsTransportId) {
                deviceSelector.transportId = consumeTransportId(channel, workBuffer, timeout)
            }
            logger.debug { "ADB transport was switched to \"${transportPrefix}\", timeout left is $timeout" }
            return channel
        }
    }

    private suspend fun consumeTransportId(
        channel: AdbInputChannel,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ): Long {
        // Transport ID is a 64-bit integer, little endian ordering
        workBuffer.clear()
        channel.readExactly(workBuffer.forChannelRead(TRANSPORT_ID_BYTE_COUNT), timeout)
        val buffer = workBuffer.afterChannelRead()
        val transportId = buffer.withOrder(ByteOrder.LITTLE_ENDIAN) { buffer.long }
        logger.debug { "Read transport id value of '${transportId}' from response" }
        return transportId
    }
}
