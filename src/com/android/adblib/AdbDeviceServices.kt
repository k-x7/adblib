package com.android.adblib

import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.MultiLineShellCollector
import com.android.adblib.utils.TextShellCollector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.time.Duration
import java.util.concurrent.TimeoutException

const val DEFAULT_SHELL_BUFFER_SIZE = 8_192

/**
 * Exposes services that are executed by the ADB daemon of a given device
 */
interface AdbDeviceServices {

    /**
     * Returns a [Flow] that, when collected, executes a shell command on a device
     * ("<device-transport>:shell" query) and emits the output of the command to the [Flow].
     *
     * The returned [Flow] elements are collected and emitted through a [ShellCollector],
     * which enables advanced use cases for collecting, mapping, filtering and joining
     * the command output which is initially collected as [ByteBuffer]. A typical use
     * case is to use a [ShellCollector] that decodes the output as a [Flow] of [String],
     * one for each line of the output.
     *
     * The flow is active until an exception is thrown, cancellation is requested by
     * the flow consumer, or the shell command is terminated.
     *
     * The flow can throw [AdbProtocolErrorException], [AdbFailResponseException],
     * [IOException] or any [Exception] thrown by [shellCollector]
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [command] the shell command to execute
     * @param [shellCollector] The [ShellCollector] invoked to collect the shell command output
     *   and emit elements to the resulting [Flow]
     * @param [commandTimeout] timeout tracking the command execution, tracking starts *after* the
     *   device connection has been successfully established. If the command takes more time than
     *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
     * @param [bufferSize] the size of the buffer used to receive data from the shell command output
     */
    fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
    ): Flow<T>
}

/**
 * Similar to [AdbDeviceServices.shell] but captures the command output as a single
 * string, decoded using the [AdbProtocolUtils.ADB_CHARSET]&nbsp;[Charset] character set.
 *
 * Note: This method should be used only for commands that output a relatively small
 * amount of text.
 */
suspend fun AdbDeviceServices.shellAsText(
    device: DeviceSelector,
    command: String,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): String {
    val collector = TextShellCollector()
    return shell(device, command, collector, commandTimeout, bufferSize).first()
}

/**
 * Similar to [AdbDeviceServices.shell] but captures the command output as a [Flow]
 * of [String], with one string for each line of the output.
 *
 * Lines are decoded using the [AdbProtocolUtils.ADB_CHARSET]&nbsp;[Charset], and line
 * terminators are detected using the [AdbProtocolUtils.ADB_NEW_LINE] character.
 *
 * Note: Each line is emitted to the flow as soon as it is received, so this method
 *       can be used to "stream" the output of a shell command without waiting for the
 *       command to terminate.
 */
fun AdbDeviceServices.shellAsLines(
    device: DeviceSelector,
    command: String,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<String> {
    val collector = MultiLineShellCollector()
    return shell(device, command, collector, commandTimeout, bufferSize)
}
