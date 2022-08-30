package com.android.adblib

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.util.concurrent.TimeUnit
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

/**
 * "Infinite" timeout, to use as the default value for optional timeouts.
 */
const val INFINITE_TIMEOUT = Long.MAX_VALUE

abstract class SystemNanoTimeProvider {

    /**
     * Returns the number of nano seconds since the system was started.
     * See [System.nanoTime]
     */
    abstract fun nanoTime(): Long

    /**
     * Throws a [TimeoutException] if [block] does not complete within the specified
     * [timeout] milliseconds.
     *
     * Unlike [withTimeout], a non-cancellable exception (i.e. [TimeoutException]) is thrown
     * when a timeout occurs.
     */
    open suspend fun <R> withErrorTimeout(
        timeout: Long,
        block: suspend CoroutineScope.() -> R
    ): R {
        return coroutineScope {
            if (timeout == INFINITE_TIMEOUT) {
                block()
            } else {
                withTimeoutOrNull(timeout) {
                    block()
                } ?: throw TimeoutException(getTimeoutMessage(timeout))
            }
        }
    }

    /**
     * Throws a [TimeoutException] if [block] does not complete within the specified
     * [timeout] milliseconds.
     *
     * Unlike [withTimeout], a non-cancellable exception (i.e. [TimeoutException]) is thrown
     * when a timeout occurs.
     */
    open suspend fun <R> withErrorTimeout(
        timeout: Long,
        unit: TimeUnit,
        block: suspend CoroutineScope.() -> R
    ): R {
        return withErrorTimeout(TimeUnit.MILLISECONDS.convert(timeout, unit), block)
    }

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
        // Note: We re-use withErrorTimeout(Long, ...) because the `withTimeoutOrNull(Long, ...)`
        //       overload using a `Duration` is experimental at the time of this writing
        return withErrorTimeout(timeout.toSafeMillis(), block)
    }

    private fun getTimeoutMessage(timeout: Long) =
        "Timeout error waiting for $timeout ms"
}

internal fun Duration.toSafeMillis(): Long {
    return try {
        this.toMillis()
    } catch (e: ArithmeticException) {
        INFINITE_TIMEOUT
    }
}

internal fun Duration.toSafeNanos(): Long {
    return try {
        this.toNanos()
    } catch (e: ArithmeticException) {
        INFINITE_TIMEOUT
    }
}
