package com.android.adblib

import com.android.adblib.impl.DevicePropertiesImpl
import com.android.adblib.impl.ShellCommandImpl
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.LineShellV2Collector
import com.android.adblib.utils.TextShellV2Collector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeoutException

const val DEFAULT_SHELL_BUFFER_SIZE = 8_192

/**
 * Exposes services that are executed by the ADB daemon of a given device
 */
interface AdbDeviceServices {

    /**
     * The session this [AdbDeviceServices] instance belongs to.
     */
    val session: AdbSession

    /**
     * Returns a [Flow] that, when collected, executes a shell command on a device
     * ("<device-transport>:shell" query) and emits the `stdout` and `stderr` output from of
     * the command to the [Flow].
     *
     * This is the equivalent of running "`/system/bin/sh -c `[command]" on the [device], meaning
     * [command] can be any arbitrary shell invocation, including pipes and redirections, as
     * opposed to executing a single process.
     *
     * __Note__: When collecting the command output, there is no way to distinguish between
     * `stdout` or `stderr`, i.e. both streams are merged. There is also no way to know
     * the `exit code` of the shell command. It is recommended to use [shellV2] instead for
     * devices that support [AdbFeatures.SHELL_V2].
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
     * @param [stdinChannel] is an optional [AdbChannel] providing bytes to send to the `stdin`
     *   of the shell command
     * @param [commandTimeout] timeout tracking the command execution, tracking starts *after* the
     *   device connection has been successfully established. If the command takes more time than
     *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
     * @param [bufferSize] the size of the buffer used to receive data from the shell command output
     * @param [shutdownOutput] shutdown device channel output end after piping [stdinChannel]
     *
     * @see [shellV2]
     */
    fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
        shutdownOutput: Boolean = true
    ): Flow<T>

    /**
     * Returns a [Flow] that, when collected, executes a shell command on a device
     * ("<device-transport>:exec" query) and emits the `stdout` output from of
     * the command to the [Flow].
     *
     * See [shell] for a more detailed description. The main difference with [shell] is this
     * service only captures `stdout` and ignores `stderr`, in addition to allowing binary
     * data transfer without mangling data.
     *
     * This service has been available since API 21, but is **not** reported as an [AdbFeatures]
     * from [AdbHostServices.features].
     *
     * See [git commit](https://android.googlesource.com/platform/system/core/+/5d9d434efadf1c535c7fea634d5306e18c68ef1f)
     *
     * __Note__: When collecting the command output, there is no way to access the contents
     * of `stderr`. There is also no way to know the `exit code` of the shell command.
     * It is recommended to use [shellV2] instead for devices that support [AdbFeatures.SHELL_V2].
     *
     * @see [shellV2]
     */
    fun <T> exec(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
        shutdownOutput: Boolean = true
    ): Flow<T>

    /**
     * Returns a [Flow] that, when collected, executes a shell command on a device
     * ("<device-transport>:shell,v2" query) and emits the output, as well as `stderr` and
     * exit code, of the command to the [Flow].
     *
     * The returned [Flow] elements are collected and emitted through a [ShellV2Collector],
     * which enables advanced use cases for collecting, mapping, filtering and joining
     * the command output which is initially collected as [ByteBuffer]. A typical use
     * case is to use a [ShellV2Collector] that decodes the output as a [Flow] of [String],
     * one for each line of the output.
     *
     * The flow is active until an exception is thrown, cancellation is requested by
     * the flow consumer, or the shell command is terminated.
     *
     * The flow can throw [AdbProtocolErrorException], [AdbFailResponseException],
     * [IOException] or any [Exception] thrown by [shellCollector].
     *
     * __Note__: Support for the "shell v2" protocol was added in Android API 24 (Nougat).
     *   To verify the protocol is supported by the target device, call the
     *   [AdbHostServices.features] method and look for the [AdbFeatures.SHELL_V2] element in the
     *   resulting [List]. If protocol is not supported by the device, the returned [Flow] throws
     *   an [AdbFailResponseException].
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [command] the shell command to execute
     * @param [shellCollector] The [ShellV2Collector] invoked to collect the shell command output
     *   and emit elements to the resulting [Flow]
     * @param [stdinChannel] is an optional [AdbChannel] providing bytes to send to the `stdin`
     *   of the shell command
     * @param [commandTimeout] timeout tracking the command execution, tracking starts *after* the
     *   device connection has been successfully established. If the command takes more time than
     *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
     * @param [bufferSize] the size of the buffer used to receive data from shell command output
     */
    fun <T> shellV2(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
    ): Flow<T>

    /**
     * Returns a [Flow] that, when collected, executes an "Android Binder Bridge" command on
     * a device ("<device-transport>:abb_exec" query) and emits the `stdout` output from of
     * the command to the [Flow]. This is the equivalent of running "`cmd `[args]" using
     * [exec], except throughput is much higher.
     *
     * __Note__: To verify the "abb" protocol is supported by the target device, callers
     * should invoke the [AdbHostServices.features] method and look for the
     * [AdbFeatures.ABB_EXEC] element in the resulting [List]. If protocol is not supported
     * by the device, the returned [Flow] throws an [AdbFailResponseException] and callers
     * should fall back to using [shellV2] or [exec] with the equivalent "`cmd`" shell command.
     *
     * __Note__: When collecting the command output, there is no way to access the contents
     * of `stderr`. There is also no way to know the `exit code` of the command. It is
     * recommended to use [abb] instead for devices that support [AdbFeatures.ABB].
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
     * @param [args] the arguments to pass to the "abb" service
     * @param [shellCollector] The [ShellCollector] invoked to collect the shell command output
     *   and emit elements to the resulting [Flow]
     * @param [stdinChannel] is an optional [AdbChannel] providing bytes to send to the `stdin`
     *   of the shell command
     * @param [commandTimeout] timeout tracking the command execution, tracking starts *after* the
     *   device connection has been successfully established. If the command takes more time than
     *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
     * @param [bufferSize] the size of the buffer used to receive data from the shell command output
     * @param [shutdownOutput] shutdown device channel output end after piping [stdinChannel]
     *
     * @see [abb]
     */
    fun <T> abb_exec(
        device: DeviceSelector,
        args: List<String>,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
        shutdownOutput: Boolean = true
    ): Flow<T>

    /**
     * Returns a [Flow] that, when collected, executes an "Android Binder Bridge" command on
     * a device ("<device-transport>:abb" query) and emits the output, as well as `stderr` and
     * exit code, of the command to the [Flow].
     *
     * The returned [Flow] elements are collected and emitted through a [ShellV2Collector],
     * which enables advanced use cases for collecting, mapping, filtering and joining
     * the command output which is initially collected as [ByteBuffer]. A typical use
     * case is to use a [ShellV2Collector] that decodes the output as a [Flow] of [String],
     * one for each line of the output.
     *
     * The flow is active until an exception is thrown, cancellation is requested by
     * the flow consumer, or the shell command is terminated.
     *
     * The flow can throw [AdbProtocolErrorException], [AdbFailResponseException],
     * [IOException] or any [Exception] thrown by [shellCollector].
     *
     * __Note__: To verify the protocol is supported by the target device, call the
     *   [AdbHostServices.features] method and look for the [AdbFeatures.ABB] element in the
     *   resulting [List]. If protocol is not supported by the device, the returned [Flow] throws
     *   an [AdbFailResponseException].
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     * @param [args] the arguments to pass to the "abb" service
     * @param [shellCollector] The [ShellV2Collector] invoked to collect the shell command output
     *   and emit elements to the resulting [Flow]
     * @param [stdinChannel] is an optional [AdbChannel] providing bytes to send to the `stdin`
     *   of the shell command
     * @param [commandTimeout] timeout tracking the command execution, tracking starts *after* the
     *   device connection has been successfully established. If the command takes more time than
     *   the timeout, a [TimeoutException] is thrown and the underlying [AdbChannel] is closed.
     * @param [bufferSize] the size of the buffer used to receive data from shell command output
     */
    fun <T> abb(
        device: DeviceSelector,
        args: List<String>,
        shellCollector: ShellV2Collector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
    ): Flow<T>

    /**
     * Opens a `sync` session on a device ("<device-transport>:sync" query) and returns
     * an instance of [AdbDeviceSyncServices] that allows performing one or more file
     * transfer operation with a device.
     *
     * The [AdbDeviceSyncServices] instance should be [closed][AutoCloseable.close]
     * when no longer in use, to ensure the underlying connection to the device is
     * closed.
     *
     * @param [device] the [DeviceSelector] corresponding to the target device
     */
    suspend fun sync(device: DeviceSelector): AdbDeviceSyncServices

    /**
     * Returns the [list][ReverseSocketList] of all
     * [reverse socket connections][ReverseSocketInfo] currently active on the
     * given [device] ("`<device-transport>:reverse:list-forward`" query).
     */
    suspend fun reverseListForward(device: DeviceSelector): ReverseSocketList

    /**
     * Creates a reverse forward socket connection from a [remote] device to the [local] host
     * ("`<device-transport>:reverse:forward(:norebind):<remote>:<local>`" query).
     *
     * This method tells the ADB Daemon of the [device] to create a [server socket][SocketSpec]
     * as specified by [remote]. The ADB Daemon listens to client connections made (on the
     * device) to that server socket, and forwards each client connection to the [SocketSpec] on
     * the host machine.
     *
     * When invoking this method, the ADB Daemon does not validate the format of the [local]
     * socket specification. A connection to the [local] socket on the host machine is made
     * only when a client connects to the [remote] server socket (on the device). At that point,
     * if [local] is invalid, the new client connection is immediately closed.
     *
     * This method fails if the device already has a reverse connection with [remote] as the
     * source, unless [rebind] is `true`.
     *
     * Returns the ADB Daemon reply to the request, typically a TCP port number if using
     * `tcp:0` for [remote].
     */
    suspend fun reverseForward(
        device: DeviceSelector,
        remote: SocketSpec,
        local: SocketSpec,
        rebind: Boolean = false
    ): String?

    /**
     * Closes a reverse socket connection on the given [device]
     * ("`<device-transport>:reverse:killforward:<remote>`" query).
     */
    suspend fun reverseKillForward(device: DeviceSelector, remote: SocketSpec)

    /**
     * Closes all reverse socket connections on the given [device]
     * ("`<device-transport>:reverse:killforward-all`" query).
     */
    suspend fun reverseKillForwardAll(device: DeviceSelector)

    /**
     * Returns a [Flow] that emits a new [ProcessIdList] everytime the set of active JDWP processes
     * on the device has changed ("`<device-transport>:track-jdwp`" query).
     *
     * Once activated, the flow remains active until cancellation (exceptional or not) occurs from
     * either the flow collector or the flow implementation, e.g. [IOException] from the
     * underlying [AdbChannel].
     */
    fun trackJdwp(device: DeviceSelector): Flow<ProcessIdList>

    /**
     * Open a JDWP connection to the [process ID][pid] and returns an [AdbChannel] for
     * that connection ("`<device-transport>:jdwp:<pid>`" query).
     *
     * The returned [AdbChannel] must be [closed][AdbChannel.close] then the JDWP
     * connection is not needed anymore.
     *
     * Note: Only **one JDWP connection** at a time can be active for a given process ID
     *   on a given device.
     *   * On API <= 28, opening a second connection immediately fails with an [IOException]
     *     ("connection refused").
     *   * On API > 29, opening a second connection is delayed until the current JDWP connection
     *     is closed.
     */
    suspend fun jdwp(device: DeviceSelector, pid: Int): AdbChannel
}

/**
 * List of process IDs as returned by [AdbDeviceServices.trackJdwp], as well as list of
 * [ErrorLine] in case some lines in the output from ADB were not recognized.
 */
typealias ProcessIdList = ListWithErrors<Int>

fun emptyProcessIdList(): ProcessIdList = emptyListWithErrors()

/**
 * Creates a [ShellCommand] to [execute][ShellCommand.execute] a shell [command] on a
 * given [device], taking advantage of features available only on more recent devices
 * (e.g. [AdbDeviceServices.shellV2]), in addition to other customization such as
 * applying an [ShellV2Collector] and configuring timeouts.
 *
 * The returned [ShellCommand] only becomes fully typed when [ShellCommand.withCollector]
 * is invoked.
 *
 * Example:
 * ```
 *     val stdout: String = shellCommand(device, "ls -l")
 *         .withCollector(TextShellV2Collector())
 *         .withCommandTimeout(Duration.ofSeconds(5))
 *         .execute()
 *         .first()
 *         .stdout
 * ```
 */
fun AdbDeviceServices.shellCommand(device: DeviceSelector, command: String): ShellCommand<*> {
    return ShellCommandImpl<Any>(this.session, device, command)
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
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): String {
    return shellCommand(device, command)
        .withTextCollector()
        .allowShellV2(false) //TODO: Remove during API cleanup
        .allowLegacyExec(false) //TODO: Remove during API cleanup
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withBufferSize(bufferSize)
        .execute()
        .first()
        .stdout
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
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<String> {
    return shellCommand(device, command)
        .withLineCollector()
        .allowShellV2(false) //TODO: Remove during API cleanup
        .allowLegacyExec(false) //TODO: Remove during API cleanup
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withBufferSize(bufferSize)
        .execute()
        .mapNotNull {
            if (it is ShellCommandOutputElement.StdoutLine) {
                it.contents
            } else {
                null
            }
        }
}

/**
 * Same as [AdbDeviceServices.shell] except that a [TimeoutException] is thrown
 * when the command does not generate any output for the [Duration] specified in
 * [commandOutputTimeout].
 */
fun <T> AdbDeviceServices.shellWithIdleMonitoring(
    device: DeviceSelector,
    command: String,
    stdoutCollector: ShellCollector<T>,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    commandOutputTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<T> {
    return shellCommand(device, command)
        .withLegacyCollector(stdoutCollector)
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withCommandOutputTimeout(commandOutputTimeout)
        .withBufferSize(bufferSize)
        .execute()
}

/**
 * Similar to [AdbDeviceServices.shellV2] but captures the command output as a single
 * [ShellCommandOutput] instance. Both [ShellCommandOutput.stdout] and [ShellCommandOutput.stderr]
 * are decoded using the [AdbProtocolUtils.ADB_CHARSET]&nbsp;[Charset] character set.
 *
 * Note: This method should be used only for commands that output a relatively small
 * amount of text.
 */
suspend fun AdbDeviceServices.shellV2AsText(
    device: DeviceSelector,
    command: String,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): ShellCommandOutput {
    return shellCommand(device, command)
        .withTextCollector()
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withBufferSize(bufferSize)
        .execute()
        .first()
}

/**
 * The result of [AdbDeviceServices.shellV2AsText]
 */
class ShellCommandOutput(
    /**
     * The shell command output captured as a single string
     */
    val stdout: String,
    /**
     * The shell command error output captured as a single string
     */
    val stderr: String,
    /**
     * The shell command exit code
     */
    val exitCode: Int
)

/**
 * Similar to [AdbDeviceServices.shellV2] but captures the command output as a [Flow] of
 * [ShellCommandOutputElement] objects, which represents the command output (both `stdout`
 * and `stderr`) split across new line boundaries. The last element of the [Flow] is
 * always a [ShellCommandOutputElement.ExitCode] element, representing the exit code
 * of the shell command.
 */
fun AdbDeviceServices.shellV2AsLines(
    device: DeviceSelector,
    command: String,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<ShellCommandOutputElement> {
    return shellCommand(device, command)
        .withLineCollector()
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withBufferSize(bufferSize)
        .execute()
}

/**
 * The base class of each entry of the [Flow] returned by [AdbDeviceServices.shellV2AsLines].
 */
sealed class ShellCommandOutputElement {

    /**
     * A `stdout` text line of the shell command.
     */
    class StdoutLine(val contents: String) : ShellCommandOutputElement() {

        // Returns the contents of the stdout line.
        override fun toString(): String = contents
    }

    /**
     * A `stderr` text line of the shell command.
     */
    class StderrLine(val contents: String) : ShellCommandOutputElement() {

        // Returns the contents of the stdout line.
        override fun toString(): String = contents
    }

    /**
     * The exit code of the shell command. This is always the last entry of the [Flow] returned by
     * [AdbDeviceServices.shellV2AsLines].
     */
    class ExitCode(val exitCode: Int) : ShellCommandOutputElement() {

        // Returns the exit code in a text form.
        override fun toString(): String = exitCode.toString()
    }
}

/**
 * The base class of each entry of the [Flow] returned by [AdbDeviceServices.shellV2AsLineBatches].
 */
sealed class BatchShellCommandOutputElement {

    /**
     * A `stdout` text lines of the shell command.
     */
    class StdoutLine(val lines: List<String>) : BatchShellCommandOutputElement()

    /**
     * A `stderr` text lines of the shell command.
     */
    class StderrLine(val lines: List<String>) : BatchShellCommandOutputElement()

    /**
     * The exit code of the shell command. This is always the last entry of the [Flow] returned by
     * [AdbDeviceServices.shellV2AsLineBatches].
     */
    class ExitCode(val exitCode: Int) : BatchShellCommandOutputElement() {

        // Returns the exit code in a text form.
        override fun toString(): String = exitCode.toString()
    }
}

/**
 * Uploads a single file to a remote device transferring the contents of [sourceChannel].
 *
 * @see [AdbDeviceSyncServices.send]
 */
suspend fun AdbDeviceServices.syncSend(
    device: DeviceSelector,
    sourceChannel: AdbInputChannel,
    remoteFilePath: String,
    remoteFileMode: RemoteFileMode,
    remoteFileTime: FileTime? = null,
    progress: SyncProgress? = null,
    bufferSize: Int = SYNC_DATA_MAX
) {
    sync(device).use {
        it.send(
            sourceChannel,
            remoteFilePath,
            remoteFileMode,
            remoteFileTime,
            progress,
            bufferSize
        )
    }
}

/**
 * Uploads a single file to a remote device transferring the contents of [sourcePath].
 *
 * @see [AdbDeviceSyncServices.send]
 */
suspend fun AdbDeviceServices.syncSend(
    device: DeviceSelector,
    sourcePath: Path,
    remoteFilePath: String,
    remoteFileMode: RemoteFileMode,
    remoteFileTime: FileTime? = null,
    progress: SyncProgress? = null,
    bufferSize: Int = SYNC_DATA_MAX
) {
    session.channelFactory.openFile(sourcePath).use { source ->
        syncSend(
            device,
            source,
            remoteFilePath,
            remoteFileMode,
            remoteFileTime,
            progress,
            bufferSize
        )
        source.close()
    }
}

/**
 * Retrieves a single file from a remote device and writes its contents to a [destinationChannel].
 *
 * @see [AdbDeviceSyncServices.recv]
 */
suspend fun AdbDeviceServices.syncRecv(
    device: DeviceSelector,
    remoteFilePath: String,
    destinationChannel: AdbOutputChannel,
    progress: SyncProgress? = null,
    bufferSize: Int = SYNC_DATA_MAX
) {
    sync(device).use {
        it.recv(
            remoteFilePath,
            destinationChannel,
            progress,
            bufferSize
        )
    }
}

/**
 * Retrieves a single file from a remote device and writes its contents to a [destinationPath].
 *
 * @see [AdbDeviceSyncServices.recv]
 */
suspend fun AdbDeviceServices.syncRecv(
    device: DeviceSelector,
    remoteFilePath: String,
    destinationPath: Path,
    progress: SyncProgress? = null,
    bufferSize: Int = SYNC_DATA_MAX
) {
    session.channelFactory.createFile(destinationPath).use { destination ->
        syncRecv(
            device,
            remoteFilePath,
            destination,
            progress,
            bufferSize
        )
        destination.close()
    }
}

/**
 * Returns a [DeviceProperties] instance for the given device. [DeviceProperties]
 * gives access to device properties returned by the `getprop` shell command.
 */
suspend fun AdbDeviceServices.deviceProperties(device: DeviceSelector): DeviceProperties {
    val cache = session.deviceCache(device)
    return cache.getOrPut(DevicePropertiesKey) {
        DevicePropertiesImpl(this, cache, device)
    }
}

private val DevicePropertiesKey = CoroutineScopeCache.Key<DeviceProperties>("DeviceProperties")

interface DeviceProperties {

    /**
     * Returns a [List] of [DeviceProperty] entries representing the result of executing
     * the `"getprop"` shell command on the device.
     */
    suspend fun all(): List<DeviceProperty>

    /**
     * Returns a subset of [all] of properties that start with `"ro."`. Since these properties
     * don't change until a device is restarted, the returned [Map] is cached as long as the
     * device is online.
     */
    suspend fun allReadonly(): Map<String, String>

    /**
     * Return the API level (as an [Int]) of the device, or [default] if an error
     * occurs.
     */
    suspend fun api(default: Int = 1): Int
}

data class DeviceProperty(val name: String, val value: String)
