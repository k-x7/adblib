package com.android.adblib

/**
 * A device definition as returned from [AdbHostServices.devices]
 */
data class DeviceInfo(
    /**
     * The serial number of the device that can be used to identify the devices
     * when sending requests to the ADB server.
     *
     * Note: Devices sometimes don't have a serial numbers, in which case they don't have
     * a corresponding [DeviceInfo] instance, i.e. this value is guaranteed to be valid.
     */
    val serialNumber: String,

    /**
     * The device state as defined in [DeviceState]. Any unknown or unsupported state is mapped
     * to [DeviceState.UNKNOWN]
     */
    val deviceState: DeviceState,

    /**
     * The product name of the device, or `null` if the value is not known.
     * This is usually the same as the "ro.product.name" property on the device.
     */
    val product: String? = null,

    /**
     * The model name of the device, or `null` if the value is not known.
     * This is usually the same as the "ro.product.model" property on the device.
     */
    val model: String? = null,

    /**
     * The device name of the device, or `null` if the value is not known.
     * This is usually the same as the "ro.product.device" property on the device.
     */
    val device: String? = null,

    /**
     * The transport identifier that can be used to identify the device when
     * sending requests to the ADB server, or `null` if the transport identifier
     * is not known.
     *
     * Note: The transport identifier is only present when using
     * [AdbHostServices.DeviceInfoFormat.LONG_FORMAT] when enumerating
     * devices with [AdbHostServices.devices]
     */
    val transportId: String? = null,

    /**
     * Optional additional fields as name-value pairs.
     */
    val additionalFields: Map<String, String> = emptyMap()
) {

    /**
     * The device state as received from the ADB server, just in case the value does not
     * fall into the pre-defined [DeviceState] enum.
     */
    val deviceStateString: String
        get() = additionalFields[RAW_DEVICE_STATE_VALUE] ?: deviceState.state

    companion object {

        private const val RAW_DEVICE_STATE_VALUE = "RAW_DEVICE_STATE_VALUE"

        internal fun fromParserValues(
            serialNumber: String,
            rawDeviceStateString: String,
            product: String? = null,
            model: String? = null,
            device: String? = null,
            transportId: String? = null,
            additionalFields: Map<String, String> = emptyMap()
        ): DeviceInfo {
            val deviceState = DeviceState.parseStateOrNull(rawDeviceStateString)

            // If we have an unknown device state value, we want to keep track of it
            // in a custom field
            val extraFields = if (deviceState == null) {
                mutableMapOf<String, String>().also {
                    it.putAll(additionalFields)
                    it[RAW_DEVICE_STATE_VALUE] = rawDeviceStateString
                }
            } else {
                additionalFields
            }

            return DeviceInfo(
                serialNumber = serialNumber,
                deviceState = deviceState ?: DeviceState.UNKNOWN,
                product = product,
                model = model,
                device = device,
                transportId = transportId,
                additionalFields = extraFields
            )
        }
    }
}

/**
 * List of [DeviceInfo] as returned by [AdbHostServices.devices], as well as list of
 * [ErrorLine] in case some lines in the output from ADB were not recognized.
 */
typealias DeviceList = ListWithErrors<DeviceInfo>
