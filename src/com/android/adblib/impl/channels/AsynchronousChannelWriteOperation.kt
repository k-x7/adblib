package com.android.adblib.impl.channels

import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.thisLogger
import com.android.adblib.impl.TimeoutTracker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.nio.channels.Channel
import java.nio.channels.CompletionHandler
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class for implementors of [AdbOutputChannel.write]
 */
internal abstract class AsynchronousChannelWriteOperation(
  protected val host: AdbSessionHost,
  timeout: Long,
  unit: TimeUnit
) : CompletionHandler<Int, CancellableContinuation<Int>> {

    private val logger = thisLogger(host)

    private val timeout = TimeoutTracker(host.timeProvider, timeout, unit)

    protected abstract val hasRemaining: Boolean

    protected abstract val channel: Channel

    protected abstract fun writeChannel(
        timeout: TimeoutTracker,
        continuation: CancellableContinuation<Int>
    )

    suspend fun execute(): Int {
        // Special case of 0 bytes
        if (!hasRemaining) {
            return 0
        }

        return suspendCancellableCoroutine { continuation ->
            // Ensure async operation is stopped if coroutine is cancelled
            channel.closeOnCancel(host, "write", continuation)

            writeAsync(continuation)
        }
    }

    private fun writeAsync(continuation: CancellableContinuation<Int>) {
        // Note: This function is *not* a `suspend`, so we need to handle exceptions and forward them to the
        // continuation to ensure to calling coroutine always completes.
        try {
            timeout.throwIfElapsed()
            writeChannel(timeout, continuation)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    override fun completed(result: Int, continuation: CancellableContinuation<Int>) {
        logger.verbose { "writeAsync completed successfully ($result bytes)" }
        continuation.resume(result)
    }

    override fun failed(exc: Throwable, continuation: CancellableContinuation<Int>) {
        val error = IOException("Error writing data to asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
