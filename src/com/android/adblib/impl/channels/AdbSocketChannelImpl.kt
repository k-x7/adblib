package com.android.adblib.impl.channels

import com.android.adblib.AdbChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementation of [AdbChannel] over an [AsynchronousSocketChannel] socket connection
 */
class AdbSocketChannelImpl(
    private val host: AdbLibHost,
    private val socketChannel: AsynchronousSocketChannel
) : AdbChannel {

    /**
     * Tell whether or not the underlying [AsynchronousSocketChannel] is open.
     */
    internal val isOpen: Boolean
        get() = socketChannel.isOpen

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

    override suspend fun shutdownInput() {
        withContext(host.ioDispatcher) {
            host.logger.debug("Shutting down input channel of ${this::class.java.simpleName}")
            @Suppress("BlockingMethodInNonBlockingContext")
            socketChannel.shutdownInput()
        }
    }

    override suspend fun shutdownOutput() {
        withContext(host.ioDispatcher) {
            host.logger.debug("Shutting down output channel of ${this::class.java.simpleName}")
            @Suppress("BlockingMethodInNonBlockingContext")
            socketChannel.shutdownOutput()
        }
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
    ) : CompletionHandler<Void?, CancellableContinuation<Unit>> {

        suspend fun execute() {
            // Note: AsynchronousSocketChannel does not support connection timeout, so we use
            // the coroutine timeout support.
            // Note: We don't need to release any resource if we hit the timeout, since the socket channel
            // is wrapped in our implementation of AutoCloseable
            return withTimeout(timeout.getRemainingTime(TimeUnit.MILLISECONDS)) {
                suspendCancellableCoroutine { continuation ->
                    socketChannel.connect(address, continuation, this@ConnectOperation)
                    // Ensure async operation is stopped if coroutine is cancelled
                    socketChannel.closeOnCancel(host, "connect", continuation)
                }
            }
        }

        override fun completed(result: Void?, continuation: CancellableContinuation<Unit>) {
            host.logger.debug("Connection completed successfully, timeout=$timeout")
            continuation.resume(Unit)
        }

        override fun failed(exc: Throwable, continuation: CancellableContinuation<Unit>) {
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

        override fun readChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Int>) {
            host.logger.debug(
                "${this::class.java.simpleName}.readChannel of maximum %d bytes",
                buffer.remaining()
            )
            socketChannel.read(buffer, timeout.remainingTime, timeout.timeUnit, continuation, this)
            // Ensure async operation is stopped if coroutine is cancelled
            socketChannel.closeOnCancel(host, "read", continuation)
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

        override fun readChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Unit>) {
            socketChannel.read(buffer, timeout.remainingTime, timeout.timeUnit, continuation, this)
            // Ensure async operation is stopped if coroutine is cancelled
            socketChannel.closeOnCancel(host, "readExactly", continuation)
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

        override fun writeChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Int>) {
            socketChannel.write(
                buffer,
                timeout.remainingTime,
                timeout.timeUnit,
                continuation,
                this
            )
            // Ensure async operation is stopped if coroutine is cancelled
            socketChannel.closeOnCancel(host, "write", continuation)
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

        override fun writeChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Unit>) {
            socketChannel.write(
                buffer,
                timeout.remainingTime,
                timeout.timeUnit,
                continuation,
                this
            )
            socketChannel.closeOnCancel(host, "writeExactly", continuation)
        }
    }
}

/**
 * Ensures an [AsynchronousSocketChannel] is immediately closed when a coroutine is cancelled
 * via its corresponding [CancellableContinuation].
 *
 * Call this method to ensure that all pending [AsynchronousSocketChannel] operations are
 * immediately terminated when a coroutine is cancelled
 *
 * [host] and [operationId] are used for logging purposes only.
 *
 * See [https://github.com/Kotlin/kotlinx.coroutines/blob/87eaba8a287285d4c47f84c91df7671fcb58271f/integration/kotlinx-coroutines-nio/src/Nio.kt#L126]
 * for the initial code this implementation is based on.
 */
private fun AsynchronousSocketChannel.closeOnCancel(
    host: AdbLibHost,
    operationId: String,
    cont: CancellableContinuation<*>
) {
    cont.invokeOnCancellation {
        try {
            host.logger.debug("Closing SocketChannel because suspended coroutine for asynchronous \"${operationId}\" has been cancelled")
            close()
        } catch (t: Throwable) {
            // Specification says that it is Ok to call it any time, but reality is different,
            // so we have just to ignore exception
            host.logger.warn(t, "Error closing SocketChannel during cancellation, ignoring")
        }
    }
}
