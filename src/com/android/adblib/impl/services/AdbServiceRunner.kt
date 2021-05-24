package com.android.adblib.impl.services

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbFailResponseException
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import com.android.adblib.utils.closeOnException
import java.nio.ByteBuffer

private const val OKAY_FAIL_BYTE_COUNT = 4
private const val LENGTH_PREFIX_BYTE_COUNT = 4

/**
 * Helper class used to perform service requests with the ADB host
 */
class AdbServiceRunner(val host: AdbLibHost, private val channelProvider: AdbChannelProvider) {

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
        val logPrefix = String.format("Running ADB server query \"%s\" -", service)
        host.logger.info("$logPrefix opening connection to ADB server, timeout=$timeout")
        channelProvider.createChannel(timeout).closeOnException { channel ->
            host.logger.info("$logPrefix sending request to ADB server, timeout=$timeout")
            sendAbdServiceRequest(channel, workBuffer, service, timeout)
            host.logger.info("$logPrefix receiving response from ADB server, timeout=$timeout")
            consumeOkayFailResponse(channel, workBuffer, timeout)
            workBuffer.clear()
            return channel
        }
    }

    private suspend fun sendAbdServiceRequest(
        channel: AdbChannel,
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
    private suspend fun consumeOkayFailResponse(
        channel: AdbChannel,
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
                    String.format(
                        "Expected \"OKAY\" or \"FAIL\" response header, got \"%s\" instead",
                        AdbProtocolUtils.bufferToByteDumpString(data)
                    )
                )
                throw error
            }
        }
    }

    private suspend fun readFailResponseAndThrow(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        timeout: TimeoutTracker
    ) {
        val data = readLengthPrefixedData(channel, workBuffer, timeout)
        val error = AdbFailResponseException(data)
        host.logger.warn("Error received from ADB server: ${error.failMessage}")
        throw error
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
        channel: AdbChannel,
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

    fun newResizableBuffer() : ResizableBuffer {
        //TODO: Consider acquiring ResizableBuffer from a pool to allow re-using instances
        return ResizableBuffer()
    }
}
