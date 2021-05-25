package com.android.adblib.impl

import com.android.adblib.AdbHostServices.DeviceInfoFormat
import com.android.adblib.DeviceErrorInfo
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceInfo.FieldEntry
import com.android.adblib.DeviceList
import com.android.adblib.utils.AdbProtocolUtils
import java.util.regex.Pattern

/** ADB returns "(no serial number)" when serial number is not available  */
private const val NO_SERIAL_PATTERN = "\\(.*\\)"

/** Any serial number that is not [.NO_SERIAL_PATTERN]  */
private const val REGULAR_SERIAL_PATTERN = "[\\S&&[^(]]\\S*"
private const val SERIAL_PATTERN = "$REGULAR_SERIAL_PATTERN|$NO_SERIAL_PATTERN"

/** TODO: This is not correct for the "no permissions" pattern  */
private const val DEVICE_STATE_PATTERN = "\\S+"
private const val ADDITIONAL_FIELDS_PATTERN = ".*"
private const val SHORT_LINE_STATE_PATTERN = ".*"

/** 3 groups: serial - state - list of additional fields  */
private val LONG_LINE_PATTERN = Pattern.compile(
    "(" + SERIAL_PATTERN + ")" + "\\s+" +
            "(" + DEVICE_STATE_PATTERN + ")" + "\\s*" +
            "(" + ADDITIONAL_FIELDS_PATTERN + ")$"
)
private val ONE_FIELD_PATTERN = Pattern.compile("(\\w+):(\\w+)")

/** 2 groups: serial - state  */
private val SHORT_LINE_PATTERN = Pattern.compile(
    "(" + SERIAL_PATTERN + ")" + "\t" +
            "(" + SHORT_LINE_STATE_PATTERN + ")$"
)

class DeviceListParser {

    fun parse(format: DeviceInfoFormat, responseText: CharSequence): DeviceList {
        val oneLineParser = when (format) {
            DeviceInfoFormat.SHORT_FORMAT -> this::parseOneShortFormatLine
            DeviceInfoFormat.LONG_FORMAT -> this::parseOneLongFormatLine
        }

        return parserWorker(responseText, oneLineParser)
    }

    private fun parserWorker(responseText: CharSequence, oneLineParser: OneLineParser): DeviceList {
        val result = ParseResult()

        // Special case of <no devices>
        if (responseText.isEmpty()) {
            return result.toDeviceList()
        }

        // There should be one device per line
        val lines = responseText.split(AdbProtocolUtils.ADB_NEW_LINE)
        for ((lineIndex, line) in lines.withIndex()) {
            if (line.isNotEmpty()) {
                oneLineParser.call(result, lineIndex, line)
            }
        }
        return result.toDeviceList()
    }

    private fun parseOneShortFormatLine(result: ParseResult, lineIndex: Int, lineText: CharSequence) {
        // Output format in ADB Host code:
        // https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/transport.cpp;l=1316
        val matcher = SHORT_LINE_PATTERN.matcher(lineText)
        if (!matcher.matches()) {
            result.addError("Device line format is not recognized", lineIndex, lineText.toString())
            return
        }
        val serialNumber = matcher.group(1)
        if (serialNumber == null || serialNumber.isEmpty()) {
            result.addError("Device serial number is empty", lineIndex, lineText)
            return
        }
        val deviceState = matcher.group(2)
        if (deviceState == null || deviceState.isEmpty()) {
            result.addError("Device state is empty", lineIndex, lineText)
            return
        }
        result.addDevice(DeviceInfo(serialNumber, deviceState, emptyList()))
    }

    private fun parseOneLongFormatLine(result: ParseResult, lineIndex: Int, lineText: CharSequence) {
        // Output format in ADB Host code:
        // https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/transport.cpp;l=1316
        val matcher = LONG_LINE_PATTERN.matcher(lineText)
        if (!matcher.matches()) {
            result.addError("Device line format is not recognized", lineIndex, lineText)
            return
        }
        val serialNumber = matcher.group(1)
        if (serialNumber == null || serialNumber.isEmpty()) {
            //TODO: If there is a "transport_id" field, we could expose the device, as it can be identified
            // with a "host-transport-id" prefix.
            result.addError("Device serial number is empty", lineIndex, lineText)
            return
        }
        val deviceState = matcher.group(2)
        if (deviceState == null || deviceState.isEmpty()) {
            result.addError("Device state is empty", lineIndex, lineText)
            return
        }
        val moreFields: MutableList<FieldEntry> = ArrayList()
        // Each field is "name:value", see ADB Host code:
        // https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/transport.cpp;l=1331
        val matcherFields = ONE_FIELD_PATTERN.matcher(lineText)
        while (matcherFields.find()) {
            val name = matcherFields.group(1)
            val value = matcherFields.group(2)
            moreFields.add(FieldEntry(name, value))
        }
        result.addDevice(DeviceInfo(serialNumber, deviceState, moreFields))
    }

    internal fun interface OneLineParser {

        fun call(result: ParseResult, lineIndex: Int, lineText: CharSequence)
    }

    internal class ParseResult(
        private val devices: MutableList<DeviceInfo> = ArrayList(),
        private val errors: MutableList<DeviceErrorInfo> = ArrayList()
    ) {

        fun addDevice(deviceInfo: DeviceInfo) {
            devices.add(deviceInfo)
        }

        fun addError(message: String, lineIndex: Int, rawLineText: CharSequence) {
            errors.add(DeviceErrorInfo(message, lineIndex, rawLineText.toString()))
        }

        fun toDeviceList(): DeviceList {
            return DeviceList(devices, errors)
        }
    }
}
