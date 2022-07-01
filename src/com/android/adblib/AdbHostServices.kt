package com.android.adblib

import com.android.adblib.utils.toImmutableList
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
     * The session this [AdbHostServices] instance belongs to.
     */
    val session: AdbSession

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
     * Note: Not all features supported by the ADB Server may be usable depending on the
     * list of features supported by a given device (see [AdbHostServices.features]).
     * Use [AdbHostServices.availableFeatures] to get the list of features supported
     * by both the ADB server and a given device.
     *
     * @see [AdbFeatures]
     * @see [AdbHostServices.availableFeatures]
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
     * See [AdbFeatures] for a (subset of the) list of possible features.
     *
     * Note: Not all features supported by a device may be usable depending on the list
     * of features supported by ADB server (see [AdbHostServices.hostFeatures]).
     * Use [AdbHostServices.availableFeatures] to get the list of features supported
     * by both the device and the ADB server.
     *
     * @see [AdbFeatures]
     * @see [AdbHostServices.availableFeatures]
     */
    suspend fun features(device: DeviceSelector): List<String>

    /**
     * Returns the list of all forward socket connections ("`host:list-forward`" query)
     * as a [list][ForwardSocketList] of [ForwardSocketInfo].
     */
    suspend fun listForward(): ForwardSocketList

    /**
     * Creates a forward socket connection from [local] to [remote]
     * ("`<device-prefix>:forward(:norebind)`" query).
     *
     * This method tells the ADB server to open a [server socket][SocketSpec] on the local machine,
     * forwarding all client connections made to that server socket to a
     * [remote socket][SocketSpec] on the specified [device].
     *
     * When invoking this method, the ADB Server does not validate the format of the [remote]
     * socket specification, nor does it connect to the [device]. A connection to the device
     * (ADB Daemon) is made only when a client connects to the local server socket. At that point,
     * if [remote] is invalid, the new client connection is immediately closed.
     *
     * This method fails if there is already a forward connection from [local], unless
     * [rebind] is `true`.
     *
     * Returns the ADB Server reply to the request, typically a TCP port number if using
     * `tcp:0` for [local]
     */
    suspend fun forward(
        device: DeviceSelector,
        local: SocketSpec,
        remote: SocketSpec,
        rebind: Boolean = false
    ): String?

    /**
     * Closes a previously created forward socket connection for the given [device]
     * ("`<device-prefix>:kill-forward`" query).
     */
    suspend fun killForward(device: DeviceSelector, local: SocketSpec)

    /**
     * Closes all previously created forward socket connections for the given [device].
     */
    suspend fun killForwardAll(device: DeviceSelector)
}

/**
 * Returns the list of features supported by both the [device] and the ADB server.
 *
 * See [AdbFeatures] for a (subset of the) list of possible features.
 */
suspend fun AdbHostServices.availableFeatures(device: DeviceSelector): List<String> {
    return this.session.deviceCache(device).getOrPutSuspending(availableFeaturesKey) {
        // We must return only the set of features common to both the host and the device.
        val deviceFeaturesSet = features(device).toSet()
        val hostFeaturesSet = hostFeatures().toSet()
        hostFeaturesSet.intersect(deviceFeaturesSet).toImmutableList()
    }
}

private val availableFeaturesKey = CoroutineScopeCache.Key<List<String>>("availableFeaturesKey")
