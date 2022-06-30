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

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.AsynchronousServerSocketChannel

/**
 * Coroutine-friendly wrapper around an [AsynchronousServerSocketChannel] with the suspending
 * [accept] method.
 */
interface AdbServerSocket : AutoCloseable {

    /**
     * Binds the channel's socket to a [local address][InetSocketAddress] and configures
     * the socket to listen for connections with the given [backLog] value.
     * Returns the assigned local address as an [InetSocketAddress].
     *
     * @see [AsynchronousServerSocketChannel.bind]
     */
    suspend fun bind(local: InetSocketAddress? = null, backLog: Int = 0): InetSocketAddress

    /**
     * Returns the socket address that this channel's socket is bound to, or `null` if
     * the socket is not [bound][bind].
     *
     * @see [bind]
     * @see [AsynchronousServerSocketChannel.getLocalAddress]
     */
    suspend fun localAddress(): InetSocketAddress?

    /**
     * Waits for a connection and returns the [AdbChannel] of the connection.
     *
     * @see [AsynchronousServerSocketChannel.accept]
     */
    suspend fun accept(): AdbChannel
}
