package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelProvider
import com.android.adblib.AdbChannelProviderFactory
import com.android.adblib.AdbSessionHost
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * An implementation of [AdbChannelProvider] that connect to an existing ADB Host running
 * on `localhost` using the port returned by the specified [portSupplier].
 */
internal class AdbChannelProviderOpenLocalHost(
  host: AdbSessionHost,
  /**
     * Supplier of the localhost port # to connect to. This is invoked lazily so that the
     * implementor has the opportunity to choose a port until an actual connection is opened,
     * i.e. in case the ADB server is started on-demand or via some other dynamic
     * configuration behavior.
     */
    private val portSupplier: suspend () -> Int
) : AdbChannelProvider {

    private val channelProvider = AdbChannelProviderFactory.createConnectAddresses(host) {
        val port = portSupplier()

        // Try IPV4 first, then IPV6
        listOf(
            InetSocketAddress("127.0.0.1", port),
            InetSocketAddress("::1", port),
        )
    }

    override suspend fun createChannel(timeout: Long, unit: TimeUnit): AdbChannel {
        return channelProvider.createChannel(timeout, unit)
    }
}
