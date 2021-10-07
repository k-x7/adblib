package com.android.adblib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * "Infinite" duration, to use as the default value for optional timeouts.
 *
 * Note: This would be a good candidate for a "class" extension of [Duration], but these
 *       are not (yet) supported, see [https://youtrack.jetbrains.com/issue/KT-11968].
 */
val INFINITE_DURATION: Duration = Duration.ofSeconds(
    Long.MAX_VALUE,  // Max allowed seconds
    999_999_999L // Max nanoseconds
)

abstract class SystemNanoTimeProvider {

    /**
     * Returns the number of nano seconds since the system was started.
     * See [System.nanoTime]
     */
    abstract fun nanoTime(): Long

    /**
     * Throws a [TimeoutException] if [block] does not complete within the specified
     * [timeout]&nbsp;[Duration].
     *
     * Unlike [withTimeout], a non-cancellable exception (i.e. [TimeoutException]) is thrown
     * when a timeout occurs. Also, the [timeout] is ignored if the value is
     * [INFINITE_DURATION].
     */
    open suspend fun <R> withErrorTimeout(
        timeout: Duration,
        block: suspend CoroutineScope.() -> R
    ): R {
        return coroutineScope {
            if (timeout == INFINITE_DURATION) {
                block()
            } else {
                // Note: We use the `withTimeoutOrNull(Long, ...)` overload because the overload
                //       using a `Duration` is experimental at the time of this writing
                withTimeoutOrNull(timeout.toSafeMillis()) {
                    block()
                } ?: throw TimeoutException(getTimeoutMessage(timeout))
            }
        }
    }

    private fun getTimeoutMessage(timeout: Duration) =
        "Timeout error waiting for ${timeout.toSafeMillis()} ms"

    private fun Duration.toSafeMillis(): Long {
        return try {
            this.toMillis()
        } catch (e: ArithmeticException) {
            Long.MAX_VALUE
        }
    }
}
