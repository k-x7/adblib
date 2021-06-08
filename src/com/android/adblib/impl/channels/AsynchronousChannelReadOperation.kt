package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.nio.channels.CompletionHandler
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Helper class for implementors of [AdbInputChannel.read]
 */
abstract class AsynchronousChannelReadOperation(
    protected val host: AdbLibHost,
    private val timeout: TimeoutTracker
) : CompletionHandler<Int, CancellableContinuation<Int>> {

    protected abstract fun readChannel(
        timeout: TimeoutTracker,
        continuation: CancellableContinuation<Int>
    )

    suspend fun execute(): Int {
        return suspendCancellableCoroutine { continuation ->
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
        host.logger.debug(
            "${this::class.java.simpleName}.readAsync completed successfully (%d bytes)",
            byteCount
        )
        continuation.resume(byteCount)
    }

    override fun failed(exc: Throwable, continuation: CancellableContinuation<Int>) {
        val error = IOException("Error reading data from asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
