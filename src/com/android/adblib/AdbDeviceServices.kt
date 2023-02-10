package com.android.adblib

import com.android.adblib.impl.DevicePropertiesImpl
import com.android.adblib.impl.ShellCommandImpl
import com.android.adblib.utils.AdbProtocolUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Duration
import java.util.concurrent.TimeoutException

const val DEFAULT_SHELL_BUFFER_SIZE = DEFAULT_BUFFER_SIZE

/**
 * Exposes services that are executed by the ADB daemon of a given device
 */
interface AdbDeviceServices {

    /**
     * The session this [AdbDeviceServices] instance belongs to.
     */
    val session: AdbSession

    /**
     * ## Note
     *
     * __It is strongly recommended to use [AdbDeviceServices.shellCommand] instead of
     * this [shell] method which is a low-level call to the `shell` services that comes
     * with a few caveats and backward compatibility issues addressed by
     * [AdbDeviceServices.shellCommand].__
     *
     * &nbsp;
     * ## Description
     *
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
     * @param [stripCrLf] Convert sequences of `CRLF` characters to `LF`. This should be used for
     *   devices at API level <= 23.
     *
     * @see AdbDeviceServices.shellCommand
     * @see shellV2
     */
    fun <T> shell(
        device: DeviceSelector,
        command: String,
        shellCollector: ShellCollector<T>,
        stdinChannel: AdbInputChannel? = null,
        commandTimeout: Duration = INFINITE_DURATION,
        bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
        shutdownOutput: Boolean = true,
        stripCrLf: Boolean = false,
    ): Flow<T>

    /**
     * ## Note
     *
     * __It is strongly recommended to use [AdbDeviceServices.shellCommand] instead of
     * this [exec] method which is a low-level call to the `shell` services that comes
     * with a few caveats and backward compatibility issues addressed by
     * [AdbDeviceServices.shellCommand].__
     *
     * &nbsp;
     * ## Description
     *
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
     * ## Note
     *
     * __It is strongly recommended to use [AdbDeviceServices.shellCommand] instead of
     * this [shellV2] method which is a low-level call to the `shell` services that comes
     * with a few caveats and backward compatibility issues addressed by
     * [AdbDeviceServices.shellCommand].__
     *
     * &nbsp;
     * ## Description
     *
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
     * Returns a [Flow] that emits a new list of [AppProcessEntry] everytime the set of active
     * processes on the device has changed ("`<device-transport>:track-app`" query).
     *
     * Once activated, the flow remains active until cancellation (exceptional or not) occurs from
     * either the flow collector or the flow implementation, e.g. [IOException] from the
     * underlying [AdbChannel].
     *
     * Note: This service was first available in Android `S` (i.e. [DeviceProperties.api] >= 31).
     */
    fun trackApp(device: DeviceSelector): Flow<List<AppProcessEntry>>

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
 * A single process entry returned by [AdbDeviceServices.trackApp]
 */
data class AppProcessEntry(
    val pid: Int,
    val debuggable: Boolean,
    val profileable: Boolean,
    val architecture: String)


/**
 * A [ShellCollector] is responsible for mapping raw binary output of a shell command,
 * provided as [ByteBuffer] instances, and emit mapped value to a [FlowCollector] of
 * type [T].
 *
 * @see [AdbDeviceServices.shellCommand]
 * @see [AdbDeviceServices.shell]
 * @see [AdbDeviceServices.exec]
 * @see [AdbDeviceServices.abb_exec]
 */
interface ShellCollector<T> {

    /**
     * Invoked by [AdbDeviceServices.shell] as soon as the shell command execution has started
     * on the device, but before any output from `stdout` has been processed.
     *
     * [collector] The [FlowCollector] where flow elements should be emitted, if any.
     */
    suspend fun start(collector: FlowCollector<T>)

    /**
     * Process a single [ByteBuffer] received from `stdout` of the shell command.
     *
     * [collector] The [FlowCollector] where flow elements should be emitted, if any.
     *
     * [stdout] The [ByteBuffer] containing a chunk of bytes collected from `stdout`.
     * For performance reasons, the buffer is only valid during the method call so the data must
     * be consumed directly in this method implementation.
     */
    suspend fun collect(collector: FlowCollector<T>, stdout: ByteBuffer)

    /**
     * Invoked when `stdout` from the command shell has reached EOF, i.e. when the command
     * execution has ended.
     *
     * [collector] The [FlowCollector] where leftover flow elements should be emitted, if any.
     */
    suspend fun end(collector: FlowCollector<T>)
}

/**
 * A [ShellV2Collector] is responsible for mapping raw binary output of a shell command,
 * provided as [ByteBuffer] instances, and emit mapped value to a [FlowCollector] of
 * type [T].
 *
 * @see [AdbDeviceServices.shellCommand]
 * @see [AdbDeviceServices.shellV2]
 * @see [AdbDeviceServices.abb]
 */
interface ShellV2Collector<T> {

    /**
     * Invoked by [AdbDeviceServices.shellV2] as soon as the shell command execution has started
     * on the device, but before any output from `stdout` has been processed.
     *
     * @param collector The [FlowCollector] where flow elements should be emitted, if any.
     */
    suspend fun start(collector: FlowCollector<T>)

    /**
     * Process a single [ByteBuffer] received from `stdout` of the shell command.
     *
     * @param collector The [FlowCollector] where flow elements should be emitted, if any.
     * @param stdout The [ByteBuffer] containing a chunk of bytes collected from `stdout`.
     *         For performance reasons, the buffer is only valid during the method
     *         call so the data must be consumed directly in this method implementation.
     */
    suspend fun collectStdout(collector: FlowCollector<T>, stdout: ByteBuffer)

    /**
     * Process a single [ByteBuffer] received from `stderr` of the shell command.
     *
     * @param collector The [FlowCollector] where flow elements should be emitted, if any.
     * @param stderr The [ByteBuffer] containing a chunk of bytes collected from `stderr`.
     *         For performance reasons, the buffer is only valid during the method call so
     *         the data must be consumed directly in this method implementation.
     */
    suspend fun collectStderr(collector: FlowCollector<T>, stderr: ByteBuffer)

    /**
     * Invoked when the shell command has exited
     *
     * @param collector The [FlowCollector] where flow elements should be emitted, if any.
     * @param exitCode The exit code of the command
     */
    suspend fun end(collector: FlowCollector<T>, exitCode: Int)
}

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
 * Use [shellCommand] to capture the command output as a single [ShellCommandOutput] instance.
 * Both [ShellCommandOutput.stdout] and [ShellCommandOutput.stderr] are decoded using
 * the [AdbProtocolUtils.ADB_CHARSET]&nbsp;[Charset] character set.
 *
 * Depending on the capabilities of the target device, either [AdbDeviceServices.shellV2],
 * [AdbDeviceServices.exec] or [AdbDeviceServices.shell] is invoked.
 *
 * [ShellCommandOutput.stderr] and [ShellCommandOutput.exitCode] are initialized
 * only if [AdbDeviceServices.shellV2] is used.
 *
 * Note: This method should be used only for commands that output a relatively small
 * amount of text.
 *
 * @see shellCommand
 * @see shellAsLines
 * @see shellAsLineBatches
 */
suspend fun AdbDeviceServices.shellAsText(
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
 * Use [shellCommand] to capture the command output as a [Flow] of [ShellCommandOutputElement],
 * typically one entry per line of `stdout` or `stderr`.
 *
 * The last element of the [Flow] is always a [ShellCommandOutputElement.ExitCode] element,
 * representing the exit code of the shell command.
 *
 * Depending on the capabilities of the target device, either [AdbDeviceServices.shellV2],
 * [AdbDeviceServices.exec] or [AdbDeviceServices.shell].
 *
 * @see [shellCommand]
 */
fun AdbDeviceServices.shellAsLines(
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
 * Use [shellCommand] to capture the command output as a [Flow] of
 * [BatchShellCommandOutputElement], which allows decoding `stdout` and `stderr`
 * as [Lists][List] of [Strings][String] decoded a binary data packets are
 * received. The size of each batch depends on the rate of output of the
 * shell command on the device and [bufferSize].
 *
 * The last element of the [Flow] is always a [BatchShellCommandOutputElement.ExitCode] element,
 * representing the exit code of the shell command.
 *
 * Depending on the capabilities of the target device, either [AdbDeviceServices.shellV2],
 * [AdbDeviceServices.exec] or [AdbDeviceServices.shell].
 *
 * @see [shellCommand]
 */
fun AdbDeviceServices.shellAsLineBatches(
    device: DeviceSelector,
    command: String,
    stdinChannel: AdbInputChannel? = null,
    commandTimeout: Duration = INFINITE_DURATION,
    bufferSize: Int = DEFAULT_SHELL_BUFFER_SIZE,
): Flow<BatchShellCommandOutputElement> {
    return shellCommand(device, command)
        .withLineBatchCollector()
        .withStdin(stdinChannel)
        .withCommandTimeout(commandTimeout)
        .withBufferSize(bufferSize)
        .execute()
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
