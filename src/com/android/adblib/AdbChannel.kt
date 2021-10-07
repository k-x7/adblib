package com.android.adblib

/**
 * Abstraction over a communication channel that can send and receive data over distinct steams,
 * i.e. a [java.nio.channels.AsynchronousSocketChannel] or [java.nio.channels.SocketChannel]
 */
interface AdbChannel : AdbInputChannel, AdbOutputChannel, AutoCloseable {

    /**
     * Shutdown the channel for reading, so that the peer receives EOF when writing.
     *
     * See [java.nio.channels.AsynchronousSocketChannel.shutdownInput]
     */
    suspend fun shutdownInput()

    /**
     * Shutdown the channel for writing, so that the peer receives EOF when reading.
     *
     * See [java.nio.channels.AsynchronousSocketChannel.shutdownOutput]
     */
    suspend fun shutdownOutput()
}
