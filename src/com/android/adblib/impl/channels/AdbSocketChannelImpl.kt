package com.android.adblib.impl.channels

import com.android.adblib.AdbChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Implementation of [AdbChannel] over an [AsynchronousSocketChannel] socket connection
 */
class AdbSocketChannelImpl(
    private val host: AdbLibHost,
    private val socketChannel: AsynchronousSocketChannel
) : AdbChannel {

    @Throws(Exception::class)
    override fun close() {
        host.logger.debug("Closing ${this::class.java.simpleName}")
        socketChannel.close()
    }

    suspend fun connect(address: InetSocketAddress, timeout: TimeoutTracker) {
        host.logger.debug("Connecting ${this::class.java.simpleName} at address $address, timeout=$timeout")
        return ConnectOperation(host, socketChannel, address, timeout).execute()
    }

    override suspend fun write(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
        return WriteOperation(host, timeout, socketChannel, buffer).execute()
    }

    override suspend fun writeExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
        return WriteExactlyOperation(host, timeout, socketChannel, buffer).execute()
    }

    override suspend fun read(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
        return ReadOperation(host, timeout, socketChannel, buffer).execute()
    }

    override suspend fun readExactly(buffer: ByteBuffer, timeout: TimeoutTracker) {
        return ReadExactlyOperation(host, timeout, socketChannel, buffer).execute()
    }

    private class ConnectOperation(
        private val host: AdbLibHost,
        private val socketChannel: AsynchronousSocketChannel,
        private val address: InetSocketAddress,
        private val timeout: TimeoutTracker
    ) : CompletionHandler<Void?, Continuation<Unit>> {

        suspend fun execute() {
            // Note: AsynchronousSocketChannel does not support connection timeout, so we use
            // the coroutine timeout support.
            // Note: We don't need to release any resource if we hit the timeout, since the socket channel
            // is wrapped in our implementation of AutoCloseable
            return withTimeout(timeout.getRemainingTime(TimeUnit.MILLISECONDS)) {
                suspendCoroutine { continuation ->
                    socketChannel.connect(address, continuation, this@ConnectOperation)
                }
            }
        }

        override fun completed(result: Void?, continuation: Continuation<Unit>) {
            host.logger.debug("Connection completed successfully, timeout=$timeout")
            continuation.resume(Unit)
        }

        override fun failed(exc: Throwable, continuation: Continuation<Unit>) {
            val error = IOException("Error connecting channel to address '$address'", exc)
            host.logger.info(error, "Connection failed")
            continuation.resumeWithException(error)
        }
    }

    private class ReadOperation(
        host: AdbLibHost,
        timeout: TimeoutTracker,
        private val socketChannel: AsynchronousSocketChannel,
        private val buffer: ByteBuffer
    ) : AsynchronousChannelReadOperation(host, timeout) {

        override fun readChannel(timeout: TimeoutTracker, continuation: Continuation<Int>) {
            host.logger.debug(
                "${this::class.java.simpleName}.readChannel of maximum %d bytes",
                buffer.remaining()
            )
            socketChannel.read(buffer, timeout.remainingTime, timeout.timeUnit, continuation, this)
        }
    }

    private class ReadExactlyOperation(
        host: AdbLibHost,
        timeout: TimeoutTracker,
        private val socketChannel: AsynchronousSocketChannel,
        private val buffer: ByteBuffer
    ) : AsynchronousChannelReadExactlyOperation(host, timeout) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override fun readChannel(timeout: TimeoutTracker, continuation: Continuation<Unit>) {
            socketChannel.read(buffer, timeout.remainingTime, timeout.timeUnit, continuation, this)
        }
    }

    private class WriteOperation(
        host: AdbLibHost,
        timeout: TimeoutTracker,
        private val socketChannel: AsynchronousSocketChannel,
        private val buffer: ByteBuffer
    ) : AsynchronousChannelWriteOperation(host, timeout) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override fun writeChannel(timeout: TimeoutTracker, continuation: Continuation<Int>) {
            socketChannel.write(
                buffer,
                timeout.remainingTime,
                timeout.timeUnit,
                continuation,
                this
            )
        }
    }

    private class WriteExactlyOperation(
        host: AdbLibHost,
        timeout: TimeoutTracker,
        private val socketChannel: AsynchronousSocketChannel,
        private val buffer: ByteBuffer
    ) : AsynchronousChannelWriteExactlyOperation(host, timeout) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override fun writeChannel(timeout: TimeoutTracker, continuation: Continuation<Unit>) {
            socketChannel.write(
                buffer,
                timeout.remainingTime,
                timeout.timeUnit,
                continuation,
                this
            )
        }
    }
}
