package com.android.adblib.impl.channels

import com.android.adblib.AdbChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.impl.remainingTimeoutToString
import com.android.adblib.thisLogger
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implementation of [AdbChannel] over an [AsynchronousSocketChannel] socket connection
 */
internal class AdbSocketChannelImpl(
  private val host: AdbSessionHost,
  private val socketChannel: AsynchronousSocketChannel
) : AdbChannel {

    private val logger = thisLogger(host)

    /**
     * Tell whether or not the underlying [AsynchronousSocketChannel] is open.
     */
    internal val isOpen: Boolean
        get() = socketChannel.isOpen

    override fun toString(): String {
        return "AdbSocketChannelImpl(${socketChannel.remoteAddress})"
    }

    @Throws(Exception::class)
    override fun close() {
        logger.debug { "Closing socket channel" }
        socketChannel.close()
    }

    suspend fun connect(address: InetSocketAddress, timeout: Long, unit: TimeUnit) {
        logger.debug { "Connecting to IP address $address, timeout=${remainingTimeoutToString(timeout, unit)}" }
        return ConnectOperation(address, timeout, unit).execute()
    }

    override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return WriteOperation(buffer, timeout, unit).execute()
    }

    override suspend fun writeExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        return WriteExactlyOperation(buffer, timeout, unit).execute()
    }

    override suspend fun shutdownInput() {
        withContext(host.ioDispatcher) {
            logger.debug { "Shutting down input channel" }
            @Suppress("BlockingMethodInNonBlockingContext")
            socketChannel.shutdownInput()
        }
    }

    override suspend fun shutdownOutput() {
        withContext(host.ioDispatcher) {
            logger.debug { "Shutting down output channel" }
            @Suppress("BlockingMethodInNonBlockingContext")
            socketChannel.shutdownOutput()
        }
    }

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return ReadOperation(buffer, timeout, unit).execute()
    }

    override suspend fun readExactly(buffer: ByteBuffer, timeout: Long, unit: TimeUnit) {
        return ReadExactlyOperation(buffer, timeout, unit).execute()
    }

    private inner class ConnectOperation(
        private val address: InetSocketAddress,
        timeout: Long,
        unit: TimeUnit
    ) : CompletionHandler<Void?, CancellableContinuation<Unit>> {

        private val timeout = TimeoutTracker(host.timeProvider, timeout, unit)

        suspend fun execute() {
            // Note: AsynchronousSocketChannel does not support connection timeout, so we use
            // the coroutine timeout support.
            // Note: We don't need to release any resource if we hit the timeout, since the socket channel
            // is wrapped in our implementation of AutoCloseable
            return withTimeout(timeout.remainingMills) {
                suspendCancellableCoroutine { continuation ->
                    // Ensure async operation is stopped if coroutine is cancelled
                    socketChannel.closeOnCancel(host, "connect", continuation)

                    socketChannel.connect(address, continuation, this@ConnectOperation)
                }
            }
        }

        override fun completed(result: Void?, continuation: CancellableContinuation<Unit>) {
            logger.debug { "Connection completed successfully, timeout=$timeout" }
            continuation.resume(Unit)
        }

        override fun failed(exc: Throwable, continuation: CancellableContinuation<Unit>) {
            val error = IOException("Error connecting channel to address '$address'", exc)
            logger.info(error) { "Connection failed" }
            continuation.resumeWithException(error)
        }
    }

    private inner class ReadOperation(
        private val buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit
    ) : AsynchronousChannelReadOperation(host, timeout, unit) {

        override val channel: Channel
            get() = socketChannel

        override fun readChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Int>) {
            logger.debug { "ReadChannel of maximum ${buffer.remaining()} bytes" }
            socketChannel.read(
                buffer,
                timeout.remainingNanos,
                TimeUnit.NANOSECONDS,
                continuation,
                this
            )
        }
    }

    private inner class ReadExactlyOperation(
        private val buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit
    ) : AsynchronousChannelReadExactlyOperation(host, timeout, unit) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override val channel: Channel
            get() = socketChannel

        override fun readChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Unit>) {
            socketChannel.read(
                buffer,
                timeout.remainingNanos,
                TimeUnit.NANOSECONDS,
                continuation,
                this
            )
        }
    }

    private inner class WriteOperation(
        private val buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit
    ) : AsynchronousChannelWriteOperation(host, timeout, unit) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override val channel: Channel
            get() = socketChannel

        override fun writeChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Int>) {
            socketChannel.write(
                buffer,
                timeout.remainingNanos,
                TimeUnit.NANOSECONDS,
                continuation,
                this
            )
        }
    }

    private inner class WriteExactlyOperation(
        private val buffer: ByteBuffer,
        timeout: Long,
        unit: TimeUnit
    ) : AsynchronousChannelWriteExactlyOperation(host, timeout, unit) {

        override val hasRemaining: Boolean
            get() = buffer.hasRemaining()

        override val channel: Channel
            get() = socketChannel

        override fun writeChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Unit>) {
            socketChannel.write(
                buffer,
                timeout.remainingNanos,
                TimeUnit.NANOSECONDS,
                continuation,
                this
            )
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
fun Closeable.closeOnCancel(
  host: AdbSessionHost,
  operationId: String,
  cont: CancellableContinuation<*>
) {
    try {
        cont.invokeOnCancellation {
            try {
                host.logger.debug { "Closing ${javaClass.simpleName} because suspended coroutine for asynchronous \"${operationId}\" has been cancelled" }
                close()
            } catch (t: Throwable) {
                // Specification says that it is Ok to call it any time, but reality is different,
                // so we have just to ignore exception
                host.logger.warn(t, "Error closing ${javaClass.simpleName} during cancellation, ignoring")
            }
        }
    } catch(t: Throwable) {
        // This can happen, for example, if invokeOnCancellation has already been called for
        // the cancellation
        host.logger.error(t, "Error registering cancellation handler for ${javaClass.simpleName}")
        throw t
    }
}
