/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbLibHost
import com.android.adblib.impl.channels.AdbSocketChannelImpl
import com.android.adblib.utils.SuppressedExceptions
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.withSuppressed
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousSocketChannel
import java.util.concurrent.TimeUnit

/**
 * An implementation of [AdbChannelProvider] that connect to an existing ADB Host running
 * on one of the addresses returned by [socketAddressesSupplier].
 */
internal class AdbChannelProviderConnectAddresses(
    private val host: AdbLibHost,
    /**
     * Supplier of the list of [InetSocketAddress] this [AdbChannelProvider] should connect to
     * locate an instance of the ADB server. This is invoked on-demand so that the
     * implementor has the opportunity to choose a port until an actual connection is opened,
     * i.e. in case the ADB server is started on-demand or via some other dynamic
     * configuration behavior.
     */
    private val socketAddressesSupplier: suspend () -> List<InetSocketAddress>
) : AdbChannelProvider {

    override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
        val tracker = TimeoutTracker(host.timeProvider, timeout, unit)

        // Runs code block on the IO Dispatcher to ensure caller is never blocked on this call
        return withContext(host.ioDispatcher) {
            host.logger.debug { "Opening ADB connection on local host addresses, timeout=$tracker" }

            // Acquire port from supplier before anything else
            val addresses = socketAddressesSupplier()
            tracker.throwIfElapsed()

            // Try all local addresses, and collect all exceptions for later reporting
            var suppressedExceptions = SuppressedExceptions.init()
            addresses.forEach { localAddress ->
                // IntelliJ warns about this due to the "throws IOException" signature
                @Suppress("BlockingMethodInNonBlockingContext")
                val socketChannel = AsynchronousSocketChannel.open(host.asynchronousChannelGroup)
                socketChannel.closeOnException {
                    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
                    val adbChannel = AdbSocketChannelImpl(host, socketChannel)
                    try {
                        adbChannel.connect(
                            localAddress,
                            tracker.remainingNanos,
                            TimeUnit.NANOSECONDS
                        )

                        // Success, return the channel
                        return@withContext adbChannel
                    } catch (e: IOException) {
                        suppressedExceptions = SuppressedExceptions.add(suppressedExceptions, e)
                    }
                }
            }
            // If we reach here, none of the addresses worked, so we bail out and throw
            // a "combined" exception
            val message = "Cannot connect to an active ADB server on any of the following " +
                    "addresses: ${addresses.joinToString { it.toString() }}"
            val error = IOException(message).withSuppressed(suppressedExceptions)
            host.logger.info(error) { "Error connecting to local ADB instance" }
            throw error
        }
    }
}
