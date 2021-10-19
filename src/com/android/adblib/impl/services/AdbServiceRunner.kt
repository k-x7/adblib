package com.android.adblib.impl.services

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.DeviceSelector
import com.android.adblib.thisLogger
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.withOrder
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val OKAY_FAIL_BYTE_COUNT = 4
private const val OKAY_FAIL_SYNC_BYTE_COUNT = 8
private const val LENGTH_PREFIX_BYTE_COUNT = 4

private const val TRANSPORT_ID_BYTE_COUNT = 8

/**
 * Helper class used to perform service requests with the ADB host
 */
class AdbServiceRunner(val host: AdbLibHost, private val channelProvider: AdbChannelProvider) {

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
        deviceSelector: DeviceSelector,
        query: String,
        timeout: TimeoutTracker
    ): String {
        val service = deviceSelector.hostPrefix + ":" + query
        val workBuffer = newResizableBuffer()
        startHostQuery(workBuffer, service, timeout).use { channel ->
            val buffer = readLengthPrefixedData(channel, workBuffer, timeout)
            logger.debug { "\"${service}\" - read ${buffer.remaining()} byte(s), timeout=$timeout" }
            return AdbProtocolUtils.byteBufferToString(buffer)
        }
    }

    suspend fun sendAbdServiceRequest(
        channel: AdbOutputChannel,
        workBuffer: ResizableBuffer,
        service: String,
        timeout: TimeoutTracker
    ) {
        // Create length prefix (4 characters) + service name
        val serviceRequest = String.format("%04X%s", service.length, service)

        workBuffer.clear()
        workBuffer.appendString(serviceRequest, AdbProtocolUtils.ADB_CHARSET)

        return channel.writeExactly(workBuffer.forChannelWrite(), timeout)
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
            return workBuffer.afterChannelRead()
        } catch (e: NumberFormatException) {
            throw AdbProtocolErrorException(
                "Invalid format for ADB response length (\"$lengthString\")", e
            )
        }
    }

    fun newResizableBuffer(): ResizableBuffer {
        //TODO: Consider acquiring ResizableBuffer from a pool to allow re-using instances
        return ResizableBuffer()
    }

    suspend fun switchToTransport(
        deviceSelector: DeviceSelector,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ): Pair<AdbChannel, Long?> {
        val transportPrefix = deviceSelector.transportPrefix
        var transportId: Long? = null
        startHostQuery(workBuffer, transportPrefix, timeout).closeOnException { channel ->
            if (deviceSelector.responseContainsTransportId) {
                transportId = consumeTransportId(channel, workBuffer, timeout)
            }
            logger.debug { "ADB transport was switched to \"${transportPrefix}\", timeout left is $timeout" }
            return Pair(channel, transportId)
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
