package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbLibHost
import com.android.adblib.impl.channels.AdbSocketChannelImpl
import com.android.adblib.utils.SuppressedExceptions
import com.android.adblib.utils.TimeoutTracker
import com.android.adblib.utils.closeOnException
import com.android.adblib.utils.withSuppressed
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousSocketChannel

private const val DEFAULT_ADB_HOST_PORT = 5037

/**
 * An implementation of [AdbChannelProvider] that connect to an existing ADB Host running
 * on `localhost` using the port returned by the specified [portSupplier]
 */
class AdbChannelProviderOpenLocalHost(
    private val host: AdbLibHost,
    /**
     * Supplier of the localhost port # to connect to. This is invoked lazily so that the
     * implementor has the opportunity to choose a port until an actual connection is opened,
     * i.e. in case the ADB server is started on-demand or via some other dynamic
     * configuration behavior.
     */
    private val portSupplier: () -> Int = { DEFAULT_ADB_HOST_PORT }
) : AdbChannelProvider {

    override suspend fun createChannel(timeout: TimeoutTracker): AdbChannel {
        // Runs code block on the IO Dispatcher to ensure caller is never blocked on this call
        return withContext(host.ioDispatcher) {
            host.logger.info("Opening ADB connection on local host addresses, timeout=$timeout")

            // Acquire port from supplier before anything else
            val port = portSupplier()
            timeout.throwIfElapsed()

            // Try all local addresses, and collect all exceptions for later reporting
            var suppressedExceptions = SuppressedExceptions.init()
            val addresses = localhostAddresses(port)
            addresses.forEach { localAddress ->
                // IntelliJ warns about this due to the "throws IOException" signature
                @Suppress("BlockingMethodInNonBlockingContext")
                val socketChannel = AsynchronousSocketChannel.open(host.asynchronousChannelGroup)
                socketChannel.closeOnException {
                    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
                    val adbChannel = AdbSocketChannelImpl(host, socketChannel)
                    try {
                        adbChannel.connect(localAddress, timeout)

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
            host.logger.info(error, "Error connecting to local ADB instance")
            throw error
        }
    }

    private fun localhostAddresses(port: Int): List<InetSocketAddress> {
        // Try IPV4 first, then IPV6
        return listOf(
            InetSocketAddress("127.0.0.1", port),
            InetSocketAddress("::1", port),
        )
    }
}
