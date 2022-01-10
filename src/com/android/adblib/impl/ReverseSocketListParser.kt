package com.android.adblib.impl

import com.android.adblib.ListWithErrors
import com.android.adblib.ReverseSocketInfo
import com.android.adblib.ReverseSocketList
import com.android.adblib.SocketSpec
import com.android.adblib.utils.AdbProtocolUtils

private const val ALL_BUT_SPACE = "[^ ]+"
private const val SPACE = " "

/**
 * Regular expression used to parse a single line
 *
 *  `<everything>SPACE<everything>SPACE<everything>`
 *
 * See implementation at:
 * [ADB source code](https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/adb_listeners.cpp;l=126)
 */
private const val REVERSE_LINE_PATTERN =
    "(${ALL_BUT_SPACE})${SPACE}(${ALL_BUT_SPACE})${SPACE}(${ALL_BUT_SPACE})"

/**
 * Parser for the list of reverse forward socket connections, as returned by the
 * `reverse:forward-list` command.
 *
 * Input example
 *
 *  ```
 *      UsbFfs tcp:5000 tcp:5003
 *      UsbFfs tcp:5001 tcp:5003
 *      UsbFfs tcp:5002 tcp:5003
 *      UsbFfs tcp:5003 tcp:5003
 *      UsbFfs tcp:5004 tcp:5003
 *  ```
 */
internal class ReverseSocketListParser {

    private val lineRegex = Regex(REVERSE_LINE_PATTERN)

    fun parse(input: CharSequence): ReverseSocketList {
        val builder = ListWithErrors.Builder<ReverseSocketInfo>()

        // Special case of <no devices>
        if (input.isEmpty()) {
            return builder.build()
        }

        // There should be one device per line
        val lines = input.split(AdbProtocolUtils.ADB_NEW_LINE)
        for ((lineIndex, line) in lines.withIndex()) {
            if (line.isNotEmpty()) {
                parseOneShortFormatLine(builder, lineIndex, line)
            }
        }
        return builder.build()
    }

    private fun parseOneShortFormatLine(
        builder: ListWithErrors.Builder<ReverseSocketInfo>,
        lineIndex: Int,
        lineText: CharSequence
    ) {
        // Output format in ADB Host code:
        // https://cs.android.com/android/platform/superproject/+/790d619575aea7032a4fe5f097d412adedf6623b:packages/modules/adb/adb_listeners.cpp;l=126
        val matchResult = lineRegex.find(lineText)
        if (matchResult == null) {
            builder.addError(
                "Forward line format is not recognized",
                lineIndex,
                lineText.toString()
            )
            return
        }

        val transportName = matchResult.groupValues[1]
        if (transportName.isEmpty()) {
            builder.addError("Transport name is empty", lineIndex, lineText)
            return
        }

        val remote = matchResult.groupValues[2]
        if (remote.isEmpty()) {
            builder.addError("Remote address is empty", lineIndex, lineText)
            return
        }
        val remoteAddress = SocketSpec.fromQueryString(remote)

        val local = matchResult.groupValues[3]
        if (local.isEmpty()) {
            builder.addError("Local address is empty", lineIndex, lineText)
            return
        }
        val localAddress = SocketSpec.fromQueryString(local)

        builder.addEntry(ReverseSocketInfo(transportName, remoteAddress, localAddress))
    }
}
