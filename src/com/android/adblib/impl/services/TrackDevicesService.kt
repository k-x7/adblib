package com.android.adblib.impl.services

import com.android.adblib.AdbChannel
import com.android.adblib.AdbHostServices.DeviceInfoFormat
import com.android.adblib.DeviceList
import com.android.adblib.impl.DeviceListParser
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.utils.TimeoutTracker
import com.android.adblib.utils.TimeoutTracker.Companion.INFINITE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

internal class TrackDevicesService(private val serviceRunner: AdbServiceRunner) {

    private val deviceParser = DeviceListParser()
    private val host
        get() = serviceRunner.host

    fun invoke(format: DeviceInfoFormat, timeout: Long, unit: TimeUnit): Flow<DeviceList> = flow {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = when (format) {
            DeviceInfoFormat.SHORT_FORMAT -> "host:track-devices"
            DeviceInfoFormat.LONG_FORMAT -> "host:track-devices-l"
        }
        val workBuffer = ResizableBuffer()
        host.logger.info("\"${service}\" - opening connection to ADB server, timeout: $tracker")

        serviceRunner.startHostQuery(workBuffer, service, tracker).use { channel ->
            collectAdbResponses(channel, workBuffer, service, format, this)
        }
    }.flowOn(host.ioDispatcher)

    private suspend fun collectAdbResponses(
        channel: AdbChannel,
        workBuffer: ResizableBuffer,
        service: String,
        format: DeviceInfoFormat,
        flowCollector: FlowCollector<DeviceList>
    ) {
        while (true) {
            // Note: We use an infinite timeout here, as the only way to end this request is to close
            //       the underlying ADB socket channel (or cancel the coroutine). This is by design.
            host.logger.info("\"${service}\" - waiting for next device tracking message")
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, INFINITE)

            // Process list of device and send it to the flow
            val deviceListString = AdbProtocolUtils.byteBufferToString(buffer)
            val devices = deviceParser.parse(format, deviceListString)

            host.logger.info("\"${service}\" - sending list of (${devices.devices.size} device(s))")
            flowCollector.emit(devices)
        }
    }
}
