package com.android.adblib

import com.android.adblib.utils.AdbProtocolUtils
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Exception thrown when a `FAIL` response is received from the ADB host
 */
class AdbFailResponseException(val failMessage: String) : IOException() {

    constructor(buffer: ByteBuffer) :
            this(extractMessageFromBuffer(buffer)) {
    }

    override val message: String
        get() = "ADB FAIL response: $failMessage"

    override fun toString(): String {
        return message
    }

    companion object {

        private fun extractMessageFromBuffer(buffer: ByteBuffer): String {
            return try {
                AdbProtocolUtils.byteBufferToString(buffer)
            } catch (e: Exception) {
                "Error retrieving FAIL message from ADB"
            }
        }
    }
}
