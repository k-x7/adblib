package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.EOFException
import java.io.IOException
import java.nio.channels.Channel
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class for implementors of [AdbInputChannel.readExactly]
 */
abstract class AsynchronousChannelReadExactlyOperation(
    protected val host: AdbLibHost,
    private val timeout: TimeoutTracker
) : CompletionHandler<Int, CancellableContinuation<Unit>> {

    protected abstract val hasRemaining: Boolean
    protected abstract val channel: Channel

    protected abstract fun readChannel(
        timeout: TimeoutTracker,
        continuation: CancellableContinuation<Unit>
    )

    suspend fun execute() {
        // Special case of 0 bytes
        if (!hasRemaining) {
            return
        }

        return suspendCancellableCoroutine { continuation ->
            // Ensure async operation is stopped if coroutine is cancelled
            channel.closeOnCancel(host, "readExactly", continuation)

            readAsync(continuation)
        }
    }

    private fun readAsync(continuation: CancellableContinuation<Unit>) {
        try {
            timeout.throwIfElapsed()
            readChannel(timeout, continuation)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    override fun completed(result: Int, continuation: CancellableContinuation<Unit>) {
        // EOF, stop reading more
        if (result == -1) {
            assert(hasRemaining) { "We should not have started a read if the buffer was already full" }
            host.logger.debug("AsynchronousSocketChannel.read reached EOF")
            continuation.resumeWithException(EOFException("Unexpected end of asynchronous channel"))
            return
        }
        host.logger.debug(
            "${javaClass.simpleName}.readAsync completed successfully (%d bytes)",
            result
        )

        // Buffer full, done reading
        if (!hasRemaining) {
            continuation.resume(Unit)
            return
        }

        // Buffer is not full, start another read (with the initial continuation)
        readAsync(continuation)
    }

    override fun failed(exc: Throwable, continuation: CancellableContinuation<Unit>) {
        val error = IOException("Error reading data from asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
