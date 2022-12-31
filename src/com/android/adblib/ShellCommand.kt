/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib

import com.android.adblib.impl.InputChannelShellOutputImpl
import com.android.adblib.impl.LineCollector
import com.android.adblib.utils.AdbBufferDecoder
import com.android.adblib.utils.FirstCollecting
import com.android.adblib.utils.firstCollecting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Supports customization of various aspects of the execution of a shell command on a device,
 * including automatically falling back from [AdbDeviceServices.shellV2] to legacy protocols
 * on older devices.
 *
 * Once a [ShellCommand] is configured with various `withXxx` methods, use the [execute]
 * method to launch the shell command execution, returning a [Flow&lt;T&gt;][Flow].
 *
 * @see [AdbDeviceServices.shellCommand]
 * @see [AdbDeviceServices.shellV2]
 * @see [AdbDeviceServices.exec]
 * @see [AdbDeviceServices.shell]
 */
interface ShellCommand<T> {

    val session: AdbSession

    /**
     * Applies a [ShellV2Collector] to transfer the raw binary shell command output.
     * This change the type of this [ShellCommand] from [T] to the final target type [U].
     */
    fun <U> withCollector(collector: ShellV2Collector<U>): ShellCommand<U>

    /**
     * Applies a legacy [ShellCollector] to transfer the raw binary shell command output.
     * This change the type of this [ShellCommand] from [T] to the final target type [U].
     */
    fun <U> withLegacyCollector(collector: ShellCollector<U>): ShellCommand<U>

    /**
     * The [AdbInputChannel] to send to the device for `stdin`.
     *
     * The default value is `null`.
     */
    fun withStdin(stdinChannel: AdbInputChannel?): ShellCommand<T>

    /**
     * Applies a [timeout] that triggers [TimeoutException] exception if the shell command
     * does not terminate within the specified [Duration].
     *
     * The default value is [INFINITE_DURATION].
     */
    fun withCommandTimeout(timeout: Duration): ShellCommand<T>

    /**
     * Applies a [timeout] that triggers a [TimeoutException] exception when the command does
     * not generate any output (`stdout` or `stderr`) for the specified [Duration].
     *
     * The default value is [INFINITE_DURATION].
     */
    fun withCommandOutputTimeout(timeout: Duration): ShellCommand<T>

    /**
     * Overrides the default buffer size used for buffering `stdout`, `stderr` and `stdin`.
     *
     * The default value is [DEFAULT_SHELL_BUFFER_SIZE].
     */
    fun withBufferSize(size: Int): ShellCommand<T>

    /**
     * Allows [execute] to use [AdbDeviceServices.shellV2] if available.
     *
     * The default value is `true`.
     */
    fun allowShellV2(value: Boolean): ShellCommand<T>

    /**
     * Allows [execute] to fall back to [AdbDeviceServices.exec] if [AdbDeviceServices.shellV2]
     * is not available or not allowed.
     *
     * The default value is `true`.
     */
    fun allowLegacyExec(value: Boolean): ShellCommand<T>

    /**
     * Allows [execute] to fall back to [AdbDeviceServices.shell] if [AdbDeviceServices.shellV2]
     * and [AdbDeviceServices.exec] are not available or not allowed.
     *
     * The default value is `true`.
     */
    fun allowLegacyShell(value: Boolean): ShellCommand<T>

    /**
     * When [execute] falls back to using the [AdbDeviceServices.shell] service,
     * and when the device API <= 23, this option allows [execute] to automatically
     * convert '\r\n' newlines (as emitted by [AdbDeviceServices.shell]) to '\n'.
     *
     * The default value is `true`.
     */
    fun allowStripCrLfForLegacyShell(value: Boolean): ShellCommand<T>

    /**
     * Allows overriding the shell command to [execute] on the device just before
     * execution starts, when the [Protocol] to be used is known.
     *
     * This can be useful, for example, for providing custom shell handling in case
     * [AdbDeviceServices.shellV2] is not supported and [execute] has to fall back to
     * [AdbDeviceServices.exec].
     *
     * The default value is a `no-op`.
     */
    fun withCommandOverride(commandOverride: (String, Protocol) -> String): ShellCommand<T>

    /**
     * Returns a [Flow] that executes the shell command on the device, according to the
     * various customization rules set by the `withXxx` methods.
     *
     * If [withCollector] or [withLegacyCollector] was not invoked before [execute],
     * an [IllegalArgumentException] is thrown, as a shell collector is mandatory.
     *
     * Once [execute] is called, further customization is not allowed.
     */
    fun execute(): Flow<T>

    /**
     * Execute the shell command on the device, assuming there is a single output
     * emitted by [ShellCommand.withCollector]. The single output is passed as
     * an argument to [block].
     *
     * Note: Unlike [execute].[first()][Flow.first], which terminates the shell command
     *  when returning the single output of the command, the shell command is still active
     *  when [block] is called.
     *
     * Note: This operator is reserved for [ShellV2Collector] that collect a single value.
     *
     * @see [Flow.firstCollecting]
     */
    suspend fun <R> executeAsSingleOutput(block: suspend (T) -> R): R {
        return execute().firstCollecting(session.scope, block)
    }

    /**
     * Execute the shell command on the device, and returns a [ShellSingleOutput] instance
     * that gives access to the single output of the command. The shell command is active
     * until [ShellSingleOutput.close] is called.
     *
     * Note: This operator is reserved for [ShellV2Collector] that collect a single value.
     *
     * @see [Flow.firstCollecting]
     */
    suspend fun executeAsSingleOutput(): ShellSingleOutput<T> {
        return ShellSingleOutput(execute().firstCollecting(session.scope))
    }

    /**
     * The protocol used for [executing][execute] a [ShellCommand]
     */
    enum class Protocol {

        SHELL_V2,
        SHELL,
        EXEC
    }
}

/**
 * Provides access to the [single output][value] of a shell command.
 *
 * @see ShellCommand.executeAsSingleOutput
 */
class ShellSingleOutput<out T>(
    firstCollecting: FirstCollecting<T>
) : FirstCollecting<T> by firstCollecting


fun <T> ShellCommand<T>.withLineCollector(): ShellCommand<ShellCommandOutputElement> {
    return this.withCollector(LineShellV2Collector())
}

fun <T> ShellCommand<T>.withLineBatchCollector(): ShellCommand<BatchShellCommandOutputElement> {
    return this.withCollector(LineBatchShellV2Collector())
}

fun <T> ShellCommand<T>.withTextCollector(): ShellCommand<ShellCommandOutput> {
    return this.withCollector(TextShellV2Collector())
}

fun <T> ShellCommand<T>.withInputChannelCollector(): ShellCommand<InputChannelShellOutput> {
    return this.withCollector(InputChannelShellCollector(this.session))
}

/**
 * A [ShellCollector] implementation that concatenates the entire `stdout` into a single [String].
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class TextShellCollector(bufferCapacity: Int = 256) : ShellCollector<String> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    /**
     * Characters accumulated during calls to [collectCharacters]
     */
    private val stringBuilder = StringBuilder()

    /**
     * We store the lambda in a field to avoid allocating an new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val characterCollector = this::collectCharacters

    override suspend fun start(collector: FlowCollector<String>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<String>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, characterCollector)
    }

    override suspend fun end(collector: FlowCollector<String>) {
        collector.emit(stringBuilder.toString())
    }

    private fun collectCharacters(charBuffer: CharBuffer) {
        stringBuilder.append(charBuffer)
    }
}

/**
 * A [ShellV2Collector] implementation that concatenates the entire output (and `stderr`) of
 * the command execution into a single [ShellCommandOutput] instance
 *
 * Note: This should be used only if the output of a shell command is expected to be somewhat
 *       small and can easily fit into memory.
 */
class TextShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<ShellCommandOutput> {

    private val stdoutCollector = TextShellCollector(bufferCapacity)
    private val stderrCollector = TextShellCollector(bufferCapacity)
    private val stdoutFlowCollector = StringFlowCollector()
    private val stderrFlowCollector = StringFlowCollector()

    override suspend fun start(collector: FlowCollector<ShellCommandOutput>) {
        stdoutCollector.start(stdoutFlowCollector)
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<ShellCommandOutput>,
        stdout: ByteBuffer
    ) {
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<ShellCommandOutput>,
        stderr: ByteBuffer
    ) {
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(collector: FlowCollector<ShellCommandOutput>, exitCode: Int) {
        stdoutCollector.end(stdoutFlowCollector)
        stderrCollector.end(stderrFlowCollector)

        val result = ShellCommandOutput(
            stdoutFlowCollector.value ?: "",
            stderrFlowCollector.value ?: "",
            exitCode
        )
        collector.emit(result)
    }

    class StringFlowCollector : FlowCollector<String> {

        var value: String? = null

        override suspend fun emit(value: String) {
            this.value = value
        }
    }
}

/**
 * The result of [AdbDeviceServices.shellAsText]
 */
class ShellCommandOutput(
    /**
     * The shell command output ("stdout") captured as a single string.
     */
    val stdout: String,
    /**
     * The shell command error output ("stderr") captured as a single string, only set if
     * [ShellCommand.Protocol] is [ShellCommand.Protocol.SHELL_V2].
     *
     * @see ShellCommand.Protocol
     */
    val stderr: String,
    /**
     * The shell command exit code, only set if [ShellCommand.Protocol] is
     * [ShellCommand.Protocol.SHELL_V2].
     */
    val exitCode: Int
)

/**
 * A [ShellCollector] implementation that collects `stdout` as a sequence of lines
 */
class LineShellCollector(bufferCapacity: Int = 256) : ShellCollector<String> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    private val lineCollector = LineCollector()

    /**
     * We store the lambda in a field to avoid allocating a new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val lineCollectorLambda: (CharBuffer) -> Unit = { lineCollector.collectLines(it) }

    override suspend fun start(collector: FlowCollector<String>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<String>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, lineCollectorLambda)

        val lines = lineCollector.getLines()
        if (lines.isEmpty()) {
            return
        }

        // The following is intentionally a tail-call so that the current method does not need to allocate
        // a continuation state.
        emitLines(lines, collector)
    }

    private suspend fun emitLines(lines: List<String>, collector: FlowCollector<String>) {
        for (line in lines) {
            collector.emit(line)
        }
        lineCollector.clear()
    }

    override suspend fun end(collector: FlowCollector<String>) {
        collector.emit(lineCollector.getLastLine())
    }

}

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class LineShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<ShellCommandOutputElement> {

    private val stdoutCollector = LineShellCollector(bufferCapacity)
    private val stderrCollector = LineShellCollector(bufferCapacity)
    private val stdoutFlowCollector = LineFlowCollector { line ->
        ShellCommandOutputElement.StdoutLine(
            line
        )
    }
    private val stderrFlowCollector = LineFlowCollector { line ->
        ShellCommandOutputElement.StderrLine(
            line
        )
    }

    override suspend fun start(collector: FlowCollector<ShellCommandOutputElement>) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<ShellCommandOutputElement>,
        stdout: ByteBuffer
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<ShellCommandOutputElement>,
        stderr: ByteBuffer
    ) {
        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(collector: FlowCollector<ShellCommandOutputElement>, exitCode: Int) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.end(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.end(stderrFlowCollector)

        collector.emit(ShellCommandOutputElement.ExitCode(exitCode))
    }

    class LineFlowCollector(
        private val builder: (String) -> ShellCommandOutputElement
    ) : FlowCollector<String> {

        var forwardingFlowCollector: FlowCollector<ShellCommandOutputElement>? = null

        override suspend fun emit(value: String) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}

/**
 * The base class of each entry of the [Flow] returned by [AdbDeviceServices.shellAsLines].
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
     * [AdbDeviceServices.shellAsLines].
     */
    class ExitCode(val exitCode: Int) : ShellCommandOutputElement() {

        // Returns the exit code in a text form.
        override fun toString(): String = exitCode.toString()
    }
}

/**
 * A [ShellCollector] implementation that collects `stdout` as a sequence of lists of lines
 */
class LineBatchShellCollector(bufferCapacity: Int = 256) : ShellCollector<List<String>> {

    private val decoder = AdbBufferDecoder(bufferCapacity)

    private val lineCollector = LineCollector()

    /**
     * We store the lambda in a field to avoid allocating a new lambda instance for every
     * invocation of [AdbBufferDecoder.decodeBuffer]
     */
    private val lineCollectorLambda: (CharBuffer) -> Unit = { lineCollector.collectLines(it) }

    override suspend fun start(collector: FlowCollector<List<String>>) {
        // Nothing to do
    }

    override suspend fun collect(collector: FlowCollector<List<String>>, stdout: ByteBuffer) {
        decoder.decodeBuffer(stdout, lineCollectorLambda)
        val lines = lineCollector.getLines()
        if (lines.isNotEmpty()) {
            collector.emit(lines.toList())
            lineCollector.clear()
        }
    }

    override suspend fun end(collector: FlowCollector<List<String>>) {
        collector.emit(listOf(lineCollector.getLastLine()))
    }
}

/**
 * A [ShellV2Collector] implementation that collects `stdout` and `stderr` as sequences of
 * [text][String] lines
 */
class LineBatchShellV2Collector(bufferCapacity: Int = 256) : ShellV2Collector<BatchShellCommandOutputElement> {

    private val stdoutCollector = LineBatchShellCollector(bufferCapacity)
    private val stderrCollector = LineBatchShellCollector(bufferCapacity)
    private val stdoutFlowCollector = LineBatchFlowCollector { lines ->
        BatchShellCommandOutputElement.StdoutLine(
            lines
        )
    }
    private val stderrFlowCollector = LineBatchFlowCollector { lines ->
        BatchShellCommandOutputElement.StderrLine(
            lines
        )
    }

    override suspend fun start(collector: FlowCollector<BatchShellCommandOutputElement>) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.start(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.start(stderrFlowCollector)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        stdout: ByteBuffer
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.collect(stdoutFlowCollector, stdout)
    }

    override suspend fun collectStderr(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        stderr: ByteBuffer
    ) {
        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.collect(stderrFlowCollector, stderr)
    }

    override suspend fun end(
        collector: FlowCollector<BatchShellCommandOutputElement>,
        exitCode: Int
    ) {
        stdoutFlowCollector.forwardingFlowCollector = collector
        stdoutCollector.end(stdoutFlowCollector)

        stderrFlowCollector.forwardingFlowCollector = collector
        stderrCollector.end(stderrFlowCollector)

        collector.emit(BatchShellCommandOutputElement.ExitCode(exitCode))
    }

    class LineBatchFlowCollector(
        private val builder: (List<String>) -> BatchShellCommandOutputElement
    ) : FlowCollector<List<String>> {

        var forwardingFlowCollector: FlowCollector<BatchShellCommandOutputElement>? = null

        override suspend fun emit(value: List<String>) {
            forwardingFlowCollector?.emit(builder(value))
        }
    }
}

/**
 * The base class of each entry of the [Flow] returned by [AdbDeviceServices.shellAsLineBatches].
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
     * [AdbDeviceServices.shellAsLineBatches].
     */
    class ExitCode(val exitCode: Int) : BatchShellCommandOutputElement() {

        // Returns the exit code in a text form.
        override fun toString(): String = exitCode.toString()
    }
}

/**
 * A [ShellV2Collector] that exposes the output of a [shellCommand] as a [InputChannelShellOutput],
 * itself exposing `stdout`, `stderr` as [AdbInputChannel] instances.
 */
class InputChannelShellCollector(
    session: AdbSession,
    bufferSize: Int = DEFAULT_BUFFER_SIZE
) : ShellV2Collector<InputChannelShellOutput> {

    private val logger = thisLogger(session)

    private val shellOutput = InputChannelShellOutputImpl(session, bufferSize)

    override suspend fun start(collector: FlowCollector<InputChannelShellOutput>) {
        collector.emit(shellOutput)
    }

    override suspend fun collectStdout(
        collector: FlowCollector<InputChannelShellOutput>,
        stdout: ByteBuffer
    ) {
        while (stdout.remaining() > 0) {
            logger.verbose { "collectStdout(${stdout.remaining()})" }
            shellOutput.writeStdout(stdout)
        }
        logger.verbose { "collectStdout: done" }
    }

    override suspend fun collectStderr(
        collector: FlowCollector<InputChannelShellOutput>,
        stderr: ByteBuffer
    ) {
        while (stderr.remaining() > 0) {
            logger.verbose { "collectStderr(${stderr.remaining()})" }
            shellOutput.writeStderr(stderr)
        }
        logger.verbose { "collectStderr: done" }
    }

    override suspend fun end(collector: FlowCollector<InputChannelShellOutput>, exitCode: Int) {
        logger.verbose { "end(exitCode=$exitCode)" }
        shellOutput.end(exitCode)
    }
}

/**
 * The [shellCommand] output when using the [InputChannelShellCollector] collector.
 */
interface InputChannelShellOutput {

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    val stdout: AdbInputChannel

    /**
     * An [AdbInputChannel] to read the contents of `stdout`. Once the shell command
     * terminates, [stdout] reaches EOF.
     */
    val stderr: AdbInputChannel

    /**
     * A [StateFlow] for the exit code of the shell command.
     * * While the command is still running, the value is `null`.
     * * Once the command terminates, the value is set to the actual
     *   (and final) exit code.
     */
    val exitCode: StateFlow<Int?>
}
