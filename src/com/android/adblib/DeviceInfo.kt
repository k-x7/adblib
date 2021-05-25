package com.android.adblib

/**
 * A device definition as returned from [AdbHostServices.devices]
 */
class DeviceInfo(
    /**
     * The serial number of the device that can be used to identify the devices
     * when sending requests to the ADB server.
     *
     * Note: Devices sometimes don't have a serial numbers, in which case they don't have
     * a corresponding [DeviceInfo] instance, i.e. this value is guaranteed to be valid.
     */
    val serialNumber: String,
    /**
     * The device state as received from the ADB server, just in case the value does not
     * fall into the pre-defined [DeviceState] enum.
     */
    rawDeviceStateString: String,
    /**
     * Optional additional fields as name-value pairs.
     */
    private val additionalFields: List<FieldEntry>
) {
    /**
     * The transport identifier that can be used to identify the device when
     * sending requests to the ADB server, or `null` if the transport identifier
     * is not known.
     *
     * Note: The transport identifier is only present when using
     * [AdbHostServices.DeviceInfoFormat.LONG_FORMAT] when enumerating
     * devices with [AdbHostServices.devices]
     */
    val transportId: String?
        get() = getAdditionalFieldValue(FieldId.TRANSPORT_ID)

    /**
     * The device state as defined in [DeviceState]. Any unknown or unsupported state is mapped
     * to [DeviceState.UNKNOWN]
     */
    val deviceState: DeviceState

    /**
     * The device state as received from the ADB server, just in case the value does not
     * fall into the pre-defined [DeviceState] enum.
     */
    val deviceStateString : String

    /**
     * The product name of the device, or `null` if the value is not known.
     * This is usually the same as the "ro.product.name" property on the device.
     */
    val product: String?
        get() = getAdditionalFieldValue(FieldId.PRODUCT)

    /**
     * The model name of the device, or `null` if the value is not known.
     * This is usually the same as the "ro.product.model" property on the device.
     */
    val model: String?
        get() = getAdditionalFieldValue(FieldId.MODEL)

    /**
     * The device name of the device, or `null` if the value is not known.
     * This is usually the same as the "ro.product.device" property on the device.
     */
    val device: String?
        get() = getAdditionalFieldValue(FieldId.DEVICE)

    init {
        val state = DeviceState.parseStateOrNull(rawDeviceStateString)
        this.deviceState = state ?: DeviceState.UNKNOWN
        // Note: We don't want to store "rawDeviceStateString" if we can get the
        //       string value from the enum, so that we don't waste memory with
        //       duplicate string values.
        this.deviceStateString = state?.toString() ?: rawDeviceStateString
    }

    private fun getAdditionalFieldValue(fieldId: FieldId): String? {
        return additionalFields
            .asSequence()
            .filter { field-> field.name == fieldId.text }
            .map { field -> field.value }
            .firstOrNull()
    }

    override fun toString(): String {
        return "Device Serial=$serialNumber, State=$deviceStateString"
    }

    enum class FieldId(val text: String) {
        PRODUCT("product"),
        MODEL("model"),
        DEVICE("device"),
        TRANSPORT_ID("transport_id");
    }

    data class FieldEntry(val name: String, val value: String)
}

/**
 * List of [devices] as returned by [AdbHostServices.devices], as well as list of
 * [errors] in case some lines in the output from ADB were not recognized.
 */
class DeviceList(val devices: List<DeviceInfo>, val errors: List<DeviceErrorInfo>)

/**
 * A device definition that was not recognized during processing in [AdbHostServices.devices]
 */
data class DeviceErrorInfo(val message: String, val lineIndex: Int, val rawLineText: String)
