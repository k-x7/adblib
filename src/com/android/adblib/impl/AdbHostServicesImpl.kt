package com.android.adblib.impl

import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import java.util.concurrent.TimeUnit

class AdbHostServicesImpl(
    private val host: AdbLibHost,
    channelProvider: AdbChannelProvider,
    private val timeout: Long,
    private val unit: TimeUnit
) : AdbHostServices {

    private val serviceRunner = AdbServiceRunner(host, channelProvider)

    override suspend fun version(): Int {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        //TODO: Consider acquiring ResizableBuffer from a pool to allow re-using instances
        val workBuffer = ResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, "host:version", tracker).use { channel ->
            val data = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val versionString = AdbProtocolUtils.byteBufferToString(data)
            try {
                return@version versionString.toInt(16)
            } catch (e: NumberFormatException) {
                val error =
                    AdbProtocolErrorException(
                        "Invalid ADB response (expected 4 digit hex. number, got \"${versionString}\" instead)",
                        e
                    )
                host.logger.warn(error, "ADB protocol error")
                throw error
            }
        }
    }
}
