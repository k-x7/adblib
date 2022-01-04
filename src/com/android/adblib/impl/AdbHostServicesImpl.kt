package com.android.adblib.impl

import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbHostServices.DeviceInfoFormat
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbLibSession
import com.android.adblib.AdbProtocolErrorException
import com.android.adblib.DeviceAddress
import com.android.adblib.DeviceList
import com.android.adblib.DeviceSelector
import com.android.adblib.DeviceState
import com.android.adblib.MdnsCheckResult
import com.android.adblib.MdnsServiceList
import com.android.adblib.PairResult
import com.android.adblib.impl.services.AdbServiceRunner
import com.android.adblib.impl.services.TrackDevicesService
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.flow.Flow
import java.io.EOFException
import java.util.concurrent.TimeUnit

internal class AdbHostServicesImpl(
    override val session: AdbLibSession,
    channelProvider: AdbChannelProvider,
    private val timeout: Long,
    private val unit: TimeUnit
) : AdbHostServices {
    private val host: AdbLibHost
        get() = session.host
    private val serviceRunner = AdbServiceRunner(session, channelProvider)
    private val deviceParser = DeviceListParser()
    private val mdnsCheckParser = MdnsCheckParser()
    private val mdnsServicesParser = MdnsServiceListParser()
    private val trackDevicesService = TrackDevicesService(serviceRunner)

    override suspend fun version(): Int {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val workBuffer = serviceRunner.newResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, "host:version", tracker).use { channel ->
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val versionString = AdbProtocolUtils.byteBufferToString(buffer)
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

    override suspend fun hostFeatures(): List<String> {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        // ADB Host implementation:
        // https://cs.android.com/android/platform/superproject/+/827f4dd859829655a03a50ebfd4dafd0d7df4421:packages/modules/adb/adb.cpp;l=1243
        val service = "host:host-features"
        val workBuffer = serviceRunner.newResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, service, tracker).use { channel ->
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val featuresString = AdbProtocolUtils.byteBufferToString(buffer)
            return@hostFeatures featuresString.split(",")
        }
    }

    override suspend fun devices(format: DeviceInfoFormat): DeviceList {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = when (format) {
            DeviceInfoFormat.SHORT_FORMAT -> "host:devices"
            DeviceInfoFormat.LONG_FORMAT -> "host:devices-l"
        }
        val workBuffer = serviceRunner.newResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, service, tracker).use { channel ->
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val deviceListString = AdbProtocolUtils.byteBufferToString(buffer)
            return@devices deviceParser.parse(format, deviceListString)
        }
    }

    override suspend fun kill() {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)

        // ADB host implementation:
        // https://cs.android.com/android/platform/superproject/+/fbcbf2500b2887952f862fa882741f80464bdbca:packages/modules/adb/adb.cpp;l=1128
        try {
            val workBuffer = serviceRunner.newResizableBuffer()
            serviceRunner.startHostQuery(workBuffer, "host:kill", tracker).use {
                host.logger.info { "ADB server was killed, timeout left is $tracker" }
            }
        } catch (e: EOFException) {
            host.logger.info {
                "Received EOF instead of OKAY response. This can happen, " +
                        "as server was killed just after sending OKAY"
            }
        }
    }

    override suspend fun mdnsCheck(): MdnsCheckResult {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/fbcbf2500b2887952f862fa882741f80464bdbca:packages/modules/adb/adb.cpp;l=1111
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "host:mdns:check"
        val workBuffer = serviceRunner.newResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, service, tracker).use { channel ->
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val outputString = AdbProtocolUtils.byteBufferToString(buffer)
            return@mdnsCheck mdnsCheckParser.parse(outputString)
        }
    }

    override suspend fun mdnsServices(): MdnsServiceList {
        // ADB Host code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/adb.cpp;l=1116
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=1945
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "host:mdns:services"
        val workBuffer = serviceRunner.newResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, service, tracker).use { channel ->
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val outputString = AdbProtocolUtils.byteBufferToString(buffer)
            return@mdnsServices mdnsServicesParser.parse(outputString)
        }
    }

    override suspend fun pair(deviceAddress: DeviceAddress, pairingCode: String): PairResult {
        // ADB Server code, service handler:
        // https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/services.cpp;l=259
        // ADB client code:
        // https://cs.android.com/android/platform/superproject/+/fbe41e9a47a57f0d20887ace0fc4d0022afd2f5f:packages/modules/adb/client/commandline.cpp;l=1741
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)
        val service = "host:pair:$pairingCode:${deviceAddress}"
        val workBuffer = serviceRunner.newResizableBuffer()
        serviceRunner.startHostQuery(workBuffer, service, tracker).use { channel ->
            val buffer = serviceRunner.readLengthPrefixedData(channel, workBuffer, tracker)
            val outputString = AdbProtocolUtils.byteBufferToString(buffer)
            // See https://cs.android.com/android/platform/superproject/+/3a52886262ae22477a7d8ffb12adba64daf6aafa:packages/modules/adb/client/adb_wifi.cpp;l=249
            val successRegex = Regex("Successfully paired to (.*) \\[guid=([^]]*)]")
            val matchResult = successRegex.find(outputString) ?: return PairResult(false, outputString)
            val serviceAddress = DeviceAddress(matchResult.groupValues[1])
            val serviceGuid = matchResult.groupValues[2]
            return PairResult(true, outputString, serviceAddress, serviceGuid)
        }
    }

    override fun trackDevices(format: DeviceInfoFormat): Flow<DeviceList> {
        return trackDevicesService.invoke(format, timeout, unit)
    }

    override suspend fun getState(device: DeviceSelector): DeviceState {
        val tracker = TimeoutTracker(timeout, unit)
        val stateString = serviceRunner.runHostDeviceQuery(device, "get-state", tracker)
        return DeviceState.parseState(stateString)
    }

    override suspend fun getSerialNo(device: DeviceSelector): String {
        val tracker = TimeoutTracker(timeout, unit)
        return serviceRunner.runHostDeviceQuery(device, "get-serialno", tracker)
    }

    override suspend fun getDevPath(device: DeviceSelector): String {
        val tracker = TimeoutTracker(timeout, unit)
        return serviceRunner.runHostDeviceQuery(device, "get-devpath", tracker)
    }

    override suspend fun features(device: DeviceSelector): List<String> {
        val tracker = TimeoutTracker(timeout, unit)
        val featuresString = serviceRunner.runHostDeviceQuery(device, "features", tracker)
        return featuresString.split(",")
    }
}
