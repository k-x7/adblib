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
package com.android.adblib.testingutils

import com.android.fakeadbserver.FakeAdbServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * Delay the execution (of unit tests) until the number of dynamic ports available
 * for opening [client sockets][java.nio.channels.SocketChannel] reaches a
 * "reasonable" level. The current implementation currently only delays execution
 * on the Windows platform and is a no-op on other platforms.
 *
 * **Background**
 *
 * Most `adblib` tests use short-lived [client sockets][java.nio.channels.SocketChannel] as
 * they typically connect to a [FakeAdbServer] instance to simulate ADB Server interaction.
 * These `localhost->localhost` sockets use "dynamic" ports, i.e. ports allocated by the
 * operating system from within a fixed range of port numbers. The range depends on the
 * operating system type (i.e. Linux, Mac and Windows) and configuration. The actual number
 * typically lies between 10,000 and 60,000, but can never exceed 65,536. On Windows, for example,
 * the range is dynamic ports starts at 49152 and ends at 65535
 * ([source](https://docs.microsoft.com/en-us/troubleshoot/windows-server/networking/default-dynamic-port-range-tcpip-chang)).
 * This limit would not be a problem in practice if ports were released as soon as a socket
 * connection is closed.
 *
 * **However**, as per TCP specification(*), a socket connection that is closed remains in
 * a `TIME_WAIT` state of some time (a few minutes typically) and the port of the connection
 * is not released until the connection exits that final state.
 *
 * Assuming an operating system configured with a TIME_WAIT value of 120 seconds and a dynamic
 * port range of 12_000, such an operating system can sustain a maximum rate 50 (short-lived)
 * socket connections per seconds: 12_000 ports divided by 120 seconds divided by 2,
 * "divided by 2" because there are 2 ports used per connection: one for the source and one for
 * the destination. A sustained higher rate eventually leads to the list of available dynamic
 * ports depleting, preventing additional connections from successfully being established,
 * typically with a "connection error" or "host not responding error" after some timeout.
 * Note that this 50 connections per second limit applies because `adblib` tests use
 * `localhost->localhost` connections.
 *
 * A couple of additional things to note:
 * * Some operating systems (e.g. Linux) have specific optimizations for `localhost->localhost`
 *   connections and skip the TIME_WAIT state entirely for such connections, hence allowing the
 *   dynamic port almost immediately after the connection is closed. This almost entirely removes
 *   the concern of dynamic port exhaustion for stress testing `adblib`. Notably, Windows does not
 *   currently have such an optimization in place.
 *
 * * The sustained connection rate limit as computed in a section above is not accurate, because
 *   there is a default "connection timeout" that some operating systems (e.g. Windows) use to
 *   delay socket connection creation when there are no dynamic ports available, effectively
 *   throttling connection creations even for localhost->localhost connections.
 */
object TimeWaitSocketsThrottler {

    /**
     * This default [Duration] value may need to be tweaked depending on how
     * long and how many tests are run in parallel. Lowering the value may lead
     * faster dynamic port exhaustion, increasing the value may lead to tests taking
     * a vey long time to run (and it would probably be better for the test to timeout instead)
     */
    private val DEFAULT_THROTTLE_DURATION: Duration = Duration.ofSeconds(60)
    private const val DEFAULT_MAX_TIME_WAIT_SOCKETS = 10_000

    fun throttleIfNeeded(
        duration: Duration = DEFAULT_THROTTLE_DURATION,
        maxTimeWaitSockets: Int = DEFAULT_MAX_TIME_WAIT_SOCKETS
    ) {
        // On Windows, delay the test for a few seconds if we have too
        // many TIME_WAIT sockets, as Windows is limited to ~16,000 dynamic
        // ports (by default) and our tests create a lot of short-lived
        // sockets.
        if (System.getProperty("os.name").startsWith("Windows")) {
            runBlocking {
                delayIfManyTimeWaitSockets(duration, maxTimeWaitSockets)
            }
        }
    }

    private suspend fun delayIfManyTimeWaitSockets(maxDuration: Duration, maxTimeWaitSockets: Int) {
        // Run the check a few times for at most "maxDuration"
        val maxDurationMillis = maxDuration.toMillis()
        val delayMillis = maxDurationMillis / 10
        withTimeoutOrNull(maxDuration.toMillis()) {
            val count = countTimeWaitSockets()
            while (count > maxTimeWaitSockets) {
                println("There are currently $count active TIME_WAIT sockets: Delaying " +
                                "test execution by approximately ${delayMillis / 1_000} seconds")
                delay(delayMillis)
            }
        }
    }

    private suspend fun countTimeWaitSockets(): Int {
        return try {
            val cmd =
                listOf("cmd", "/c", "netstat", "-an", "-p", "tcp", "|", "findstr", "TIME_WAIT")
            val result = SuspendableProcessExecutor().execute(cmd)

            if (result.exitCode != 0 && result.stderr.isNotEmpty() && result.stdout.isEmpty()) {
                throw Exception("Netstat process execution failed with exit code ${result.exitCode}")
            }

            // Number of lines is the number of TIME_WAIT sockets
            result.stdout.size
        } catch (t: Throwable) {
            System.err.println("Error counting number of TIME_WAIT sockets, assuming none. Exception follows:")
            t.printStackTrace(System.err)
            return 0
        }
    }
}
