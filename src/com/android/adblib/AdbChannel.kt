package com.android.adblib

/**
 * Abstraction over a communication channel that can send and receive data over distinct steams,
 * i.e. a [java.nio.channels.AsynchronousSocketChannel] or [java.nio.channels.SocketChannel]
 */
interface AdbChannel : AdbInputChannel, AdbOutputChannel, AutoCloseable {
}
