package com.android.adblib.impl

import com.android.adblib.ForwardSocketInfo
import com.android.adblib.ForwardSocketList
import com.android.adblib.ListWithErrors
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
private const val FORWARD_LINE_PATTERN =
    "(${ALL_BUT_SPACE})${SPACE}(${ALL_BUT_SPACE})${SPACE}(${ALL_BUT_SPACE})"

/**
 * Parser for the list of forward socket connection returned by the `host:forward-list` command.
 *
 * Input example
 *
 *  ```
 *     adb-939AX05XBZ-vWgJpq	_adb-tls-connect._tcp.	192.168.1.86:39149
 *     adb-939AX05XBZ-vWgJpq	_adb-tls-pairing._tcp.	192.168.1.86:37313
 *  ```
 */
internal class ForwardSocketListParser {

    private val lineRegex = Regex(FORWARD_LINE_PATTERN)

    fun parse(input: CharSequence): ForwardSocketList {
        val builder = ListWithErrors.Builder<ForwardSocketInfo>()

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
        builder: ListWithErrors.Builder<ForwardSocketInfo>,
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

        val serialNumber = matchResult.groupValues[1]
        if (serialNumber.isEmpty()) {
            builder.addError("Device serial number is empty", lineIndex, lineText)
            return
        }

        val local = matchResult.groupValues[2]
        if (local.isEmpty()) {
            builder.addError("Local address is empty", lineIndex, lineText)
            return
        }
        val localAddress = SocketSpec.fromQueryString(local)

        val remote = matchResult.groupValues[3]
        if (remote.isEmpty()) {
            builder.addError("Remote address is empty", lineIndex, lineText)
            return
        }
        val remoteAddress = SocketSpec.fromQueryString(remote)

        builder.addEntry(ForwardSocketInfo(serialNumber, localAddress, remoteAddress))
    }
}
