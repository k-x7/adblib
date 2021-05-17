package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbLibHost
import com.android.adblib.utils.TimeoutTracker
import java.io.IOException
import java.nio.channels.CompletionHandler
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Helper class for implementors of [AdbInputChannel.read]
 */
abstract class AsynchronousChannelReadOperation(
    protected val host: AdbLibHost,
    private val timeout: TimeoutTracker
) : CompletionHandler<Int, Continuation<Int>> {

    protected abstract fun readChannel(timeout: TimeoutTracker, continuation: Continuation<Int>)

    suspend fun execute(): Int {
        return suspendCoroutine { continuation ->
            readAsync(continuation)
        }
    }

    private fun readAsync(continuation: Continuation<Int>) {
        try {
            timeout.throwIfElapsed()
            readChannel(timeout, continuation)
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
        }
    }

    override fun completed(byteCount: Int, continuation: Continuation<Int>) {
        host.logger.debug("${this::class.java.simpleName}.readAsync completed successfully (%d bytes)", byteCount)
        continuation.resume(byteCount)
    }

    override fun failed(exc: Throwable, continuation: Continuation<Int>) {
        val error = IOException("Error reading data from asynchronous channel", exc)
        continuation.resumeWithException(error)
    }
}
