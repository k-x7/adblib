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
 * Helper class for implementors of [AdbOutputChannel.writeExactly]
 */
internal abstract class AsynchronousChannelWriteExactlyOperation(
  protected val host: AdbSessionHost,
  timeout: Long,
  unit: TimeUnit
) : CompletionHandler<Int, CancellableContinuation<Unit>> {

    private val logger = thisLogger(host)

    private val timeout = TimeoutTracker(host.timeProvider, timeout, unit)

    protected abstract val hasRemaining: Boolean

    protected abstract val channel: Channel

    protected abstract fun writeChannel(timeout: TimeoutTracker, continuation: CancellableContinuation<Unit>)

    suspend fun execute() {
       // Special case of 0 bytes
       if (!hasRemaining) {
           return
       }
       return suspendCancellableCoroutine { continuation ->
           // Ensure async operation is stopped if coroutine is cancelled
           channel.closeOnCancel(host, "writeExactly", continuation)

           writeAsync(continuation)
       }
   }

   private fun writeAsync(continuation: CancellableContinuation<Unit>) {
       try {
           timeout.throwIfElapsed()
           writeChannel(timeout, continuation)
       } catch (t: Throwable) {
           continuation.resumeWithException(t)
       }
   }

   override fun completed(result: Int, continuation: CancellableContinuation<Unit>) {
       logger.debug { "writeAsync completed successfully ($result bytes)" }
       // Buffer is fully read, finish operation
       if (!hasRemaining) {
           continuation.resume(Unit)
           return
       }

       // Start another write operation, with the same continuation
       writeAsync(continuation)
   }

   override fun failed(exc: Throwable, continuation: CancellableContinuation<Unit>) {
       val error = IOException("Error writing data to asynchronous channel", exc)
       continuation.resumeWithException(error)
   }
}
