package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
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
 * Helper class for implementors of [AdbInputChannel.read]
 */
internal abstract class AsynchronousChannelReadOperation(
  protected val host: AdbSessionHost,
  timeout: Long,
  unit: TimeUnit
) : CompletionHandler<Int, CancellableContinuation<Int>> {

    private val logger = thisLogger(host)

    private val timeout = TimeoutTracker(host.timeProvider, timeout, unit)

    protected abstract val channel: Channel

    protected abstract fun readChannel(
        timeout: TimeoutTracker,
        continuation: CancellableContinuation<Int>
    )

    suspend fun execute(): Int {
        return suspendCancellableCoroutine { continuation ->
            // Ensure async operation is stopped if coroutine is cancelled
            channel.closeOnCancel(host, "read", continuation)

            readAsync(continuation)
        }
    }

    private fun readAsync(continuation: CancellableContinuation<Int>) {
        try {
            timeout.throwIfElapsed()
            readChannel(timeout, continuation)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    override fun completed(byteCount: Int, continuation: CancellableContinuation<Int>) {
        logger.verbose { "readAsync completed successfully ($byteCount bytes)" }
        continuation.resume(byteCount)
    }

    override fun failed(exc: Throwable, continuation: CancellableContinuation<Int>) {
        val error = IOException("Error reading data from asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
