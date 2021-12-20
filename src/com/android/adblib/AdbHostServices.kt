package com.android.adblib

import kotlinx.coroutines.flow.Flow

/**
 * Exposes services specific to the ADB Server (or "host") as `suspend` functions
 *
 * The underlying implementation is responsible for creating connections to the ADB server
 * as needed, as well as ensuring resources are released when coroutines complete or
 * are cancelled.
 */
interface AdbHostServices {

    /**
     * Returns the internal version of the ADB server ("host:version" query).
     *
     * The internal version is an integer value that is incremented when newer builds of ADB
     * are incompatible with older ADB clients. This value is somewhat opaque to
     * public consumers, but this API is provided for completeness.
     */
    suspend fun version(): Int

    /**
     * Returns the list of features supported by the ADB server ("host:host-features" query).
     *
     * Note that these features may not be supported by the ADB daemon running on a device,
     * use [features] to retrieve the list of features supported by a device.
     */
    suspend fun hostFeatures(): List<String>

    /**
     * Returns the list of devices known to the ADB Server as a [DeviceList] object
     * ("host:devices" query).
     *
     * Use the [format] parameter to specify how much information to collect for each
     * device ([short][DeviceInfoFormat.SHORT_FORMAT] or [long][DeviceInfoFormat.LONG_FORMAT]
     * format supported).
     */
    suspend fun devices(format: DeviceInfoFormat = DeviceInfoFormat.SHORT_FORMAT): DeviceList

    /**
     * Returns a [Flow] that emits a new [DeviceList] everytime a device state change is
     * detected by the ADB Host ("host:track-devices" query). The flow is active until
     * an exception is thrown or cancellation is requested by the flow consumer.
     */
    fun trackDevices(format: DeviceInfoFormat = DeviceInfoFormat.SHORT_FORMAT): Flow<DeviceList>

    enum class DeviceInfoFormat {
        /**
         * [DeviceInfo.serialNumber] and [DeviceInfo.deviceState] only
         */
        SHORT_FORMAT,

        /**
         * [DeviceInfo.serialNumber], [DeviceInfo.deviceState], and additional fields, such as [DeviceInfo.transportId]
         */
        LONG_FORMAT
    }

    /**
     * Kills the running instance of the ADB server ("host:kill" query).
     */
    suspend fun kill()

    /**
     * Checks mDNS is supported on this version of ADB ("host:mdns:check" query).
     */
    suspend fun mdnsCheck(): MdnsCheckResult

    /**
     * Returns a list of mDNS services known to the ADB server ("host:mdns:services" query).
     */
    suspend fun mdnsServices(): MdnsServiceList

    /**
     * Pairs this ADB server with a device given its [deviceAddress] and a [pairingCode].
     */
    suspend fun pair(deviceAddress: DeviceAddress, pairingCode: String): PairResult

    /**
     * Returns the [DeviceState] of the [device] ("<device-prefix>:get-state" query).
     */
    suspend fun getState(device: DeviceSelector): DeviceState

    /**
     * Returns the serial number of the [device] ("<device-prefix>:get-serialno" query).
     */
    suspend fun getSerialNo(device: DeviceSelector): String

    /**
     * Returns the `dev-path` of the [device] ("<device-prefix>:get-devpath" query).
     */
    suspend fun getDevPath(device: DeviceSelector): String

    /**
     * Returns the list of features of the [device] ("<device-prefix>:features" query).
     */
    suspend fun features(device: DeviceSelector): List<String>

}
