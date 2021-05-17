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
 * Helper class for implementors of [AdbOutputChannel.writeExactly]
 */
abstract class AsynchronousChannelWriteExactlyOperation(
    private val host: AdbLibHost,
    private val timeout: TimeoutTracker
) : CompletionHandler<Int, Continuation<Unit>> {

    protected abstract val hasRemaining: Boolean

    protected abstract fun writeChannel(timeout: TimeoutTracker, continuation: Continuation<Unit>)

    suspend fun execute() {
       // Special case of 0 bytes
       if (!hasRemaining) {
           return
       }
       return suspendCoroutine { continuation ->
           writeAsync(continuation)
       }
   }

   private fun writeAsync(continuation: Continuation<Unit>) {
       try {
           timeout.throwIfElapsed()
           writeChannel(timeout, continuation)
       } catch (t: Throwable) {
           continuation.resumeWithException(t)
       }
   }

   override fun completed(result: Int, continuation: Continuation<Unit>) {
       host.logger.debug("${this::class.java.simpleName}.writeAsync completed successfully (%d bytes)", result)
       // Buffer is fully read, finish operation
       if (!hasRemaining) {
           continuation.resume(Unit)
           return
       }

       // Start another write operation, with the same continuation
       writeAsync(continuation)
   }

   override fun failed(exc: Throwable, continuation: Continuation<Unit>) {
       val error = IOException("Error writing data to asynchronous channel", exc)
       continuation.resumeWithException(error)
   }
}
