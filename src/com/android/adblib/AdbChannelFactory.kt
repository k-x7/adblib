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
package com.android.adblib

import com.android.adblib.impl.channels.AdbInputChannelSliceImpl
import com.android.adblib.impl.channels.ByteBufferAdbInputChannelImpl
import com.android.adblib.impl.channels.ByteBufferAdbOutputChannelImpl
import com.android.adblib.impl.channels.DEFAULT_CHANNEL_BUFFER_SIZE
import com.android.adblib.impl.channels.EmptyAdbInputChannelImpl
import com.android.adblib.utils.ResizableBuffer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Factory class for various implementations of [AdbInputChannel] and [AdbOutputChannel]
 */
interface AdbChannelFactory {

    /**
     * Opens an existing file, and returns an [AdbInputChannel] for reading from it.
     *
     * @throws IOException if the operation fails
     */
    suspend fun openFile(path: Path): AdbInputChannel

    /**
     * Creates a new file, or truncates an existing file, and returns an [AdbOutputChannel]
     * for writing to it.
     *
     * @throws IOException if the operation fails
     */
    suspend fun createFile(path: Path): AdbOutputChannel

    /**
     * Creates a new file that does not already exist on disk, and returns an [AdbOutputChannel]
     * for writing to it.
     *
     * @throws IOException if the operation fails or if the file already exists on disk
     */
    suspend fun createNewFile(path: Path): AdbOutputChannel

    /**
     * Opens a client socket and connects to the given [remote address][InetSocketAddress],
     * returning an [AdbChannel] for sending/receiving data.
     *
     * @throws IOException if the operation fails
     * @see AsynchronousSocketChannel
     * @see AsynchronousSocketChannel.connect
     */
    suspend fun connectSocket(
        remote: InetSocketAddress,
        timeout: Long = Long.MAX_VALUE,
        unit: TimeUnit = TimeUnit.MILLISECONDS
    ): AdbChannel

    /**
     * Creates an [AdbServerSocket], a coroutine friendly version of
     * [AsynchronousServerSocketChannel].
     *
     * @throws IOException if the operation fails
     * @see AsynchronousServerSocketChannel.open
     */
    suspend fun createServerSocket(): AdbServerSocket

    /**
     * Creates an [AdbPipedInputChannel] that can be fed data in a thread-safe way from
     * an [AdbOutputChannel], i.e. write operations to the [AdbOutputChannel]
     * end up feeding pending (or future) [read] operations on this [AdbPipedInputChannel].
     *
     * @see java.io.PipedInputStream
     * @see java.io.PipedOutputStream
     */
    fun createPipedChannel(bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE): AdbPipedInputChannel
}

/**
 * Creates an [AdbInputChannel] that contains no data.
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun EmptyAdbInputChannel(): AdbInputChannel {
    return EmptyAdbInputChannelImpl
}

/**
 * Returns an [AdbInputChannel] that reads at most [length] bytes from another [AdbInputChannel].
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun AdbInputChannelSlice(inputChannel: AdbInputChannel, length: Int): AdbInputChannel {
    return AdbInputChannelSliceImpl(inputChannel, length)
}

/**
 * Returns an [AdbInputChannel] that reads bytes from a [ByteBuffer]. Once all bytes
 * are read (i.e. [AdbInputChannel.read] returns -1), the [ByteBuffer.remaining] value
 * is zero.
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun ByteBufferAdbInputChannel(buffer: ByteBuffer): AdbInputChannel {
    return ByteBufferAdbInputChannelImpl(buffer)
}

/**
 * Returns an [AdbOutputChannel] that appends bytes to a [ResizableBuffer]. The
 * [ResizableBuffer][buffer] grows as needed to allow [AdbOutputChannel.write] calls
 * to succeed.
 *
 * Once [AdbOutputChannel.close] is called, use [ResizableBuffer.forChannelWrite] to
 * access the underlying [ByteBuffer] that will contain data written to the buffer
 * from [ByteBuffer.position] `0` to [ByteBuffer.limit].
 */
@Suppress("FunctionName") // Mirroring coroutines API, with many functions that look like constructors.
fun ByteBufferAdbOutputChannel(buffer: ResizableBuffer): AdbOutputChannel {
    return ByteBufferAdbOutputChannelImpl(buffer)
}

