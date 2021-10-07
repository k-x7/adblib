package com.android.adblib.utils

import com.android.adblib.SystemNanoTimeProvider
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Utility class to keep track of how much time is left given an initial timeout. This is useful
 * when a method receives a timeout parameter and needs to perform multiple operations within that
 * given timeout.
 *
 * Note: The implementation keeps track of time using [System.nanoTime] units, meaning
 * this class is not suitable for timeouts longer than ~290 years.
 */
class TimeoutTracker(
    /** The [SystemNanoTimeProvider] to use as source of time */
    private val nanoTimeProvider: SystemNanoTimeProvider,
    /** The timeout expressed in [timeUnit] units. */
    private val timeout: Long,
    /** The [TimeUnit] of the timeout */
    val timeUnit: TimeUnit
) {

    /**
     * Value of system nano time when the timer is created, meaning overflowing occurs every ~290+
     * years.
     */
    private val startNanos: Long = elapsedNanos(0)

    constructor(timeout: Long, unit: TimeUnit) : this(SystemNanoTime.instance, timeout, unit)

    /**
     * Returns the time until this timeout expires, in [timeUnit] units.
     */
    val remainingTime: Long
        get() = getRemainingTime(timeUnit)

    val remainingMills: Long
        get() = getRemainingTime(TimeUnit.MILLISECONDS)

    /**
     * Returns the time until this timeout expires in [TimeUnit.NANOSECONDS] units.
     */
    val remainingNanos: Long
        get() {
            // This needs to handle Long.MAX_VALUE for timeout, i.e. we cannot
            // overflow. This is ok as elapsedNanos() will never be negative.
            return timeUnit.toNanos(timeout) - elapsedNanos(startNanos)
        }

    /**
     * Returns the amount of time until this timeout expires, in [TimeUnit] units.
     */
    fun getRemainingTime(unit: TimeUnit): Long {
        // Using TimeUnit convert() ensures overflows are taken care of
        return unit.convert(remainingNanos, TimeUnit.NANOSECONDS)
    }

    /**
     * Returns the amount of time elapsed since the timeout started, in [TimeUnit] units.
     */
    fun getElapsedTime(unit: TimeUnit): Long {
        // Using TimeUnit convert() ensures overflows are taken care of
        return unit.convert(elapsedNanos(startNanos), TimeUnit.NANOSECONDS)
    }

    override fun toString(): String {
        return String.format(
            "%,.1f msec remaining, %,.1f msec elapsed",
            getRemainingTime(TimeUnit.MILLISECONDS).toDouble(),
            getElapsedTime(TimeUnit.MILLISECONDS).toDouble()
        )
    }

    /**
     * Throws a [TimeoutException] if this timeout has expired
     */
    @Throws(TimeoutException::class)
    fun throwIfElapsed() {
        if (remainingTime < 0) {
            throw TimeoutException("Operation has timed out")
        }
    }

    private fun elapsedNanos(startNanos: Long): Long {
        return nanoTimeProvider.nanoTime() - startNanos
    }

    companion object {
        val INFINITE = TimeoutTracker(Long.MAX_VALUE, TimeUnit.NANOSECONDS)
    }
}
