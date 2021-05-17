package com.android.adblib.impl.channels

import com.android.adblib.AdbLibHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.utils.TimeoutTracker
import java.io.IOException
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Helper class for implementors of [AdbOutputChannel.write]
 */
abstract class AsynchronousChannelWriteOperation(
    private val host: AdbLibHost,
    private val timeout: TimeoutTracker
) : CompletionHandler<Int, Continuation<Int>> {

    protected abstract val hasRemaining: Boolean

    protected abstract fun writeChannel(timeout: TimeoutTracker, continuation: Continuation<Int>)

    suspend fun execute(): Int {
        // Special case of 0 bytes
        if (!hasRemaining) {
            return 0
        }

        return suspendCoroutine { continuation ->
            writeAsync(continuation)
        }
    }

    private fun writeAsync(continuation: Continuation<Int>) {
        // Note: This function is *not* a `suspend`, so we need to handle exceptions and forward them to the
        // continuation to ensure to calling coroutine always completes.
        try {
            timeout.throwIfElapsed()
            writeChannel(timeout, continuation)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    override fun completed(result: Int, continuation: Continuation<Int>) {
        host.logger.debug(
            "${this::class.java.simpleName}.writeAsync completed successfully (%d bytes)",
            result
        )
        continuation.resume(result)
    }

    override fun failed(exc: Throwable, continuation: Continuation<Int>) {
        val error = IOException("Error writing data to asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
