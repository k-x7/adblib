package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.utils.TimeoutTracker
import java.io.EOFException
import java.io.IOException
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Helper class for implementors of [AdbInputChannel.readExactly]
 */
abstract class AsynchronousChannelReadExactlyOperation(
    private val host: AdbLibHost,
    private val timeout: TimeoutTracker
) : CompletionHandler<Int, Continuation<Unit>> {

    protected abstract val hasRemaining: Boolean

    protected abstract fun readChannel(timeout: TimeoutTracker, continuation: Continuation<Unit>)

    suspend fun execute() {
        // Special case of 0 bytes
        if (!hasRemaining) {
            return
        }

        return suspendCoroutine { continuation ->
            readAsync(continuation)
        }
    }

    private fun readAsync(continuation: Continuation<Unit>) {
        try {
            timeout.throwIfElapsed()
            readChannel(timeout, continuation)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    override fun completed(result: Int, continuation: Continuation<Unit>) {
        // EOF, stop reading more
        if (result == -1) {
            assert(hasRemaining) { "We should not have started a read if the buffer was already full" }
            host.logger.debug("AsynchronousSocketChannel.read reached EOF")
            continuation.resumeWithException(EOFException("Unexpected end of asynchronous channel"))
            return
        }
        host.logger.debug(
            "${this::class.java.simpleName}.readAsync completed successfully (%d bytes)",
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

    override fun failed(exc: Throwable, continuation: Continuation<Unit>) {
        val error = IOException("Error reading data from asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
