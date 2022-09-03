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
package com.android.adblib.impl

import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.DeviceSelector
import com.android.adblib.ShellCollector
import com.android.adblib.ShellV2Collector
import com.android.adblib.SystemNanoTimeProvider
import com.android.adblib.impl.ShellWithIdleMonitoring.HeartbeatRecorder.FlowEntry.*
import com.android.adblib.thisLogger
import com.android.adblib.toSafeMillis
import com.android.adblib.toSafeNanos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.TimeoutException

internal abstract class ShellWithIdleMonitoring<T, TShellCollector>(
    private val parameters: Parameters<TShellCollector>
) {

    /**
     * Convenience class used to pass all parameters in one go
     */
    class Parameters<TShellCollector>(
        val deviceServices: AdbDeviceServices,
        val device: DeviceSelector,
        val command: String,
        val shellCollector: TShellCollector,
        val stdinChannel: AdbInputChannel?,
        val commandTimeout: Duration,
        val commandOutputTimeout: Duration,
        val bufferSize: Int,
        val stripCrLf: Boolean
    )

    private val deviceServices: AdbDeviceServices
        get() = parameters.deviceServices

    private val host: AdbSessionHost
        get() = deviceServices.session.host

    fun createFlow(): Flow<T> = flow {
        // To detect a command that does not produce any output for the given timeout:
        // 1) we intercept the original ShellCollector, forwarding all messages and
        //    recording "heartbeats" every time we see output coming in
        // 2) at the same time, we run a concurrent coroutine, the "heartbeat detector" that
        //    checks incoming "heartbeats" from the shell command . If there is no "heartbeat"
        //    for longer than the timeout, the coroutine throws an `TimeoutException`, which
        //    cancels the whole flow with a `TimeoutException`.
        val heartbeat = HeartbeatRecorder(host.timeProvider)
        val forwardingCollector = createForwardingCollector(host, heartbeat, parameters.shellCollector)
        val heartbeatDetector = HeartbeatDetector(host, heartbeat, parameters.commandOutputTimeout)
        coroutineScope {
            // Launch our command (in)activity detector
            launch {
                heartbeatDetector.run()
            }

            // Start our regular shell command execution
            val shellFlow = execute(parameters, forwardingCollector)

            // Forward from the regular shell flow to the returned flow
            shellFlow.collect {
                emit(it)
            }

            // Ensure the idle detector exits its loop
            heartbeat.close()
        }
    }.flowOn(host.ioDispatcher)

    abstract fun createForwardingCollector(
      host: AdbSessionHost,
      heartbeat: HeartbeatRecorder,
      delegate: TShellCollector
    ): TShellCollector

    abstract fun execute(
        parameters: Parameters<TShellCollector>,
        forwardingCollector: TShellCollector
    ): Flow<T>

    /**
     * A [ShellCollector] that forwards all messages to another [ShellCollector]
     * while at the same time emits "heartbeats" on a [SendChannel] when messages
     * are forwarded.
     */
    protected class ForwardingShellCollector<T>(
      host: AdbSessionHost,
      private val heartbeat: HeartbeatRecorder,
      private val delegate: ShellCollector<T>
    ) : ShellCollector<T> {

        private val logger = thisLogger(host)

        override suspend fun start(collector: FlowCollector<T>) {
            logger.verbose { "start" }
            heartbeat.recordNow()
            delegate.start(collector)
        }

        override suspend fun collect(collector: FlowCollector<T>, stdout: ByteBuffer) {
            logger.verbose { "collect" }
            heartbeat.recordNow()
            delegate.collect(collector, stdout)
        }

        override suspend fun end(collector: FlowCollector<T>) {
            logger.verbose { "end" }
            heartbeat.recordNow()
            delegate.end(collector)
        }
    }

    /**
     * A [ShellV2Collector] that forwards all messages to another [ShellV2Collector]
     * while at the same time emits "heartbeats" on a [SendChannel] when messages
     * are forwarded.
     */
    protected class ForwardingShellV2Collector<T>(
      host: AdbSessionHost,
      private val heartbeat: HeartbeatRecorder,
      private val delegate: ShellV2Collector<T>
    ) : ShellV2Collector<T> {

        private val logger = thisLogger(host)

        override suspend fun start(collector: FlowCollector<T>) {
            logger.verbose { "start" }
            heartbeat.recordNow()
            delegate.start(collector)
        }

        override suspend fun collectStdout(collector: FlowCollector<T>, stdout: ByteBuffer) {
            logger.verbose { "collect" }
            heartbeat.recordNow()
            delegate.collectStdout(collector, stdout)
        }

        override suspend fun collectStderr(collector: FlowCollector<T>, stderr: ByteBuffer) {
            logger.verbose { "collect" }
            heartbeat.recordNow()
            delegate.collectStderr(collector, stderr)
        }

        override suspend fun end(collector: FlowCollector<T>, exitCode: Int) {
            logger.verbose { "end" }
            heartbeat.recordNow()
            delegate.end(collector, exitCode)
        }
    }

    /**
     * A coroutine helper class that ensures [heartbeat] is updated at least every
     * [commandIdleTimeout] duration.
     */
    protected class HeartbeatDetector(
      private val host: AdbSessionHost,
      private val heartbeat: HeartbeatRecorder,
      private val commandIdleTimeout: Duration
    ) {
        private val timeProvider: SystemNanoTimeProvider
            get() = host.timeProvider

        suspend fun run() {
            val probingTimeout = commandIdleTimeout.dividedBy(2)

            // Wait until the command is actually executing (without timeout, as this initial
            // message is received after connecting to ADB and setting the command, etc.)
            var lastHeartbeatNanoTime = heartbeat.waitForFirst()

            // Now, we can start looking for heartbeats. If at any point in time, the
            // shell command terminates, heartbeat throws CancellationException.
            while (true) {
                try {
                    // Wait for specified command activity timeout, restarting the timeout
                    // everytime we detect a heartbeat.
                    // In all cases, we measure actual elapsed time from the last heartbeat
                    // before deciding if the specified timeout has been exceeded.
                    host.timeProvider.withErrorTimeout(probingTimeout) {
                        // Wait until new heartbeat is emitted.
                        val nextHeartbeatDeadline = lastHeartbeatNanoTime + probingTimeout
                        lastHeartbeatNanoTime = heartbeat.waitForNext(nextHeartbeatDeadline)
                        throwIfInactive()
                    }
                } catch (e: TimeoutException) {
                    throwIfInactive()
                }
            }
        }

        private fun throwIfInactive() {
            val nanosFromLastHeartbeat = timeProvider.nanoTime() - heartbeat.lastRecorded.nanos
            val timeoutNanos = commandIdleTimeout.toSafeNanos()
            if (nanosFromLastHeartbeat >= timeoutNanos) {
                throw TimeoutException("Command has been inactive for more than " +
                                       "${commandIdleTimeout.toSafeMillis()} millis")
            }
        }
    }

    class HeartbeatRecorder(private val timeProvider: SystemNanoTimeProvider) {

        private val lastHeartbeatFlow = MutableStateFlow<FlowEntry>(Heartbeat(NanoTime.MIN_VALUE))

        private val closed: Boolean
            get() = lastHeartbeatFlow.value === Closed

        val lastRecorded: NanoTime
            get() {
                return when(val entry = lastHeartbeatFlow.value) {
                    is Closed -> throwCancellation()
                    is Heartbeat -> entry.nanoTime
                }
            }

        /**
         * Records a heartbeat now
         */
        fun recordNow() {
            if (closed) {
                throwCancellation()
            }
            lastHeartbeatFlow.value = Heartbeat(NanoTime(timeProvider.nanoTime()))
        }

        /**
         * Waits for at least one heartbeat to be [recorded][recordNow]
         */
        suspend fun waitForFirst(): NanoTime {
             return waitWhile { it == NanoTime.MIN_VALUE }
        }

        /**
         * Waits for at least one recorded heartbeat recorded after the given [lastNanoTime]
         */
        suspend fun waitForNext(lastNanoTime: NanoTime): NanoTime {
            return waitWhile { it <= lastNanoTime }
        }

        fun close() {
            lastHeartbeatFlow.value = Closed
        }

        private suspend fun waitWhile(predicate: (NanoTime) -> Boolean): NanoTime {
            lastHeartbeatFlow.takeWhile {
                predicate(lastRecorded)
            }.collect()

            return lastRecorded
        }

        private fun throwCancellation(): Nothing {
            throw CancellationException("Heartbeat recorder has been closed")
        }

        private sealed class FlowEntry {
            object Closed : FlowEntry()
            class Heartbeat(val nanoTime: NanoTime) : FlowEntry()
        }
    }

    @JvmInline
    value class NanoTime(val nanos: Long) {

        operator fun compareTo(other: NanoTime): Int {
            return nanos.compareTo(other.nanos)
        }

        operator fun plus(duration: Duration): NanoTime {
            return NanoTime(nanos + duration.toSafeNanos())
        }

        companion object {
            val MIN_VALUE = NanoTime(Long.MIN_VALUE)
        }
    }
}

internal class ShellV2WithIdleMonitoring<T>(
    parameters: Parameters<ShellV2Collector<T>>
) : ShellWithIdleMonitoring<T, ShellV2Collector<T>>(parameters) {

    override fun createForwardingCollector(
      host: AdbSessionHost,
      heartbeat: HeartbeatRecorder,
      delegate: ShellV2Collector<T>
    ): ShellV2Collector<T> {
        return ForwardingShellV2Collector(host, heartbeat, delegate)
    }

    override fun execute(
        parameters: Parameters<ShellV2Collector<T>>,
        forwardingCollector: ShellV2Collector<T>
    ): Flow<T> {
        return parameters.deviceServices.shellV2(
            parameters.device,
            parameters.command,
            forwardingCollector,
            parameters.stdinChannel,
            parameters.commandTimeout,
            parameters.bufferSize
        )
    }
}

internal class LegacyExecWithIdleMonitoring<T>(
    parameters: Parameters<ShellCollector<T>>
) : ShellWithIdleMonitoring<T, ShellCollector<T>>(parameters) {

    override fun createForwardingCollector(
      host: AdbSessionHost,
      heartbeat: HeartbeatRecorder,
      delegate: ShellCollector<T>
    ): ShellCollector<T> {
        return ForwardingShellCollector(host, heartbeat, delegate)
    }

    override fun execute(
        parameters: Parameters<ShellCollector<T>>,
        forwardingCollector: ShellCollector<T>
    ): Flow<T> {
        return parameters.deviceServices.exec(
            parameters.device,
            parameters.command,
            forwardingCollector,
            parameters.stdinChannel,
            parameters.commandTimeout,
            parameters.bufferSize
        )
    }
}

internal class LegacyShellWithIdleMonitoring<T>(
    parameters: Parameters<ShellCollector<T>>
) : ShellWithIdleMonitoring<T, ShellCollector<T>>(parameters) {

    override fun createForwardingCollector(
      host: AdbSessionHost,
      heartbeat: HeartbeatRecorder,
      delegate: ShellCollector<T>
    ): ShellCollector<T> {
        return ForwardingShellCollector(host, heartbeat, delegate)
    }

    override fun execute(
        parameters: Parameters<ShellCollector<T>>,
        forwardingCollector: ShellCollector<T>
    ): Flow<T> {
        return parameters.deviceServices.shell(
            parameters.device,
            parameters.command,
            forwardingCollector,
            parameters.stdinChannel,
            parameters.commandTimeout,
            parameters.bufferSize,
            stripCrLf = parameters.stripCrLf
        )
    }
}
