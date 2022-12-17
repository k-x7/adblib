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
package com.android.adblib.impl.channels

import com.android.adblib.AdbBufferedOutputChannel
import com.android.adblib.AdbChannel
import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbInputChannel
import com.android.adblib.impl.AdbPipedInputChannelImpl
import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbPipedInputChannel
import com.android.adblib.AdbServerSocket
import com.android.adblib.AdbSession
import com.android.adblib.impl.AdbBufferedInputChannelImpl
import com.android.adblib.impl.AdbReadAheadInputChannel
import com.android.adblib.impl.AdbWriteBackOutputChannel
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit

internal class AdbChannelFactoryImpl(private val session: AdbSession) : AdbChannelFactory {
    private val host: AdbSessionHost
        get() = session.host

    override suspend fun openFile(path: Path): AdbInputChannel {
        return openInput(path, StandardOpenOption.READ)
    }

    override suspend fun createFile(path: Path): AdbOutputChannel {
        return openOutput(
            path,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    override suspend fun createNewFile(path: Path): AdbOutputChannel {
        return openOutput(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

    override suspend fun connectSocket(
        remote: InetSocketAddress,
        timeout: Long,
        unit: TimeUnit
    ): AdbChannel {
        return withContext(host.ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            AsynchronousSocketChannel.open(host.asynchronousChannelGroup)
                .closeOnException { socketChannel ->
                    socketChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
                    AdbSocketChannelImpl(host, socketChannel).closeOnException { socket ->
                        socket.connect(remote, timeout, unit)
                        socket
                    }
                }
        }
    }

    override suspend fun createServerSocket(): AdbServerSocket {
        return withContext(host.ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            AsynchronousServerSocketChannel.open(host.asynchronousChannelGroup)
                .closeOnException { serverSocketChannel ->
                    AdbServerSocketImpl(host, serverSocketChannel)
                }
        }
    }

    override fun createPipedChannel(bufferSize: Int): AdbPipedInputChannel {
        return AdbPipedInputChannelImpl(session, bufferSize)
    }

    override fun createBufferedInputChannel(input: AdbInputChannel, bufferSize: Int): AdbInputChannel {
        return AdbBufferedInputChannelImpl(session, input, bufferSize)
    }

    override fun createReadAheadChannel(
        input: AdbInputChannel,
        bufferSize: Int
    ): AdbInputChannel {
        return AdbReadAheadInputChannel(session, input, bufferSize)
    }

    override fun createWriteBackChannel(
        output: AdbOutputChannel,
        bufferSize: Int
    ): AdbBufferedOutputChannel {
        return AdbWriteBackOutputChannel(session, output, bufferSize)
    }

    private suspend fun openOutput(
        path: Path,
        vararg options: OpenOption
    ): AdbOutputFileChannel {
        return withContext(host.ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            val fileChannel = AsynchronousFileChannel.open(path, *options)
            fileChannel.closeOnException {
                AdbOutputFileChannel(host, path, fileChannel)
            }
        }
    }

    private suspend fun openInput(
        path: Path,
        vararg options: OpenOption
    ): AdbInputFileChannel {
        return withContext(host.ioDispatcher) {
            @Suppress("BlockingMethodInNonBlockingContext")
            val fileChannel = AsynchronousFileChannel.open(path, *options)
            fileChannel.closeOnException {
                AdbInputFileChannel(host, path, fileChannel)
            }
        }
    }
}
