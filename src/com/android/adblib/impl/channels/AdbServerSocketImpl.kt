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
package com.android.adblib.impl.channels

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbServerSocket
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine-friendly wrapper around an [AsynchronousServerSocketChannel] with the suspending
 * [bind] and [accept] methods.
 */
internal class AdbServerSocketImpl(
    private val host: AdbSessionHost,
    private val serverSocketChannel: AsynchronousServerSocketChannel
) : AdbServerSocket {

    private val completionHandler = CompletionHandlerAdapter()

    override suspend fun localAddress(): InetSocketAddress? {
        return withContext(host.ioDispatcher) {
            serverSocketChannel.localAddress as? InetSocketAddress
        }
    }

    override suspend fun bind(local: InetSocketAddress?, backLog: Int): InetSocketAddress {
        return withContext(host.ioDispatcher) {
            val localAddress = local ?: InetSocketAddress(Inet4Address.getLoopbackAddress(), 0)
            @Suppress("BlockingMethodInNonBlockingContext")
            serverSocketChannel.bind(localAddress, backLog)
            serverSocketChannel.localAddress as InetSocketAddress
        }
    }

    override suspend fun accept(): AdbChannel {
        return withContext(host.ioDispatcher) {
            runAccept().closeOnException { asyncSocket ->
                asyncSocket.setOption(StandardSocketOptions.TCP_NODELAY, true)
                AdbSocketChannelImpl(host, asyncSocket)
            }
        }
    }

    private suspend fun runAccept(): AsynchronousSocketChannel {
        return suspendCancellableCoroutine { continuation ->
            // Ensure that the asynchronous operation is stopped if the coroutine is cancelled.
            serverSocketChannel.closeOnCancel(host, "accept", continuation)

            serverSocketChannel.accept(continuation, completionHandler)
        }
    }

    override fun close() {
        serverSocketChannel.close()
    }

    private class CompletionHandlerAdapter :
        CompletionHandler<AsynchronousSocketChannel, CancellableContinuation<AsynchronousSocketChannel>> {

        override fun completed(
            socketChannel: AsynchronousSocketChannel,
            continuation: CancellableContinuation<AsynchronousSocketChannel>
        ) {
            continuation.resume(socketChannel)
        }

        override fun failed(
            e: Throwable,
            continuation: CancellableContinuation<AsynchronousSocketChannel>
        ) {
            continuation.resumeWithException(e)
        }
    }
}
