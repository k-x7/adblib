/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.adblib.impl

import com.android.adblib.SystemNanoTimeProvider
import com.android.adblib.utils.SystemNanoTime
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

private const val INFINITE_TIMEOUT = Long.MAX_VALUE

/**
 * Utility class to keep track of how much time is left given an initial timeout. This is useful
 * when a method receives a timeout parameter and needs to perform multiple operations within that
 * given timeout.
 *
 * Note: The implementation keeps track of time using [System.nanoTime] units, meaning
 * this class is not suitable for timeouts longer than ~290 years.
 */
internal class TimeoutTracker(
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
     * Returns the time until this timeout expires in [TimeUnit.MILLISECONDS] units.
     */
    val remainingMills: Long
        get() = getRemainingTime(TimeUnit.MILLISECONDS)

    /**
     * Returns the time until this timeout expires in [TimeUnit.NANOSECONDS] units.
     */
    val remainingNanos: Long
        get() {
            return if (timeout == INFINITE_TIMEOUT) {
                INFINITE_TIMEOUT
            } else {
                // This needs to handle Long.MAX_VALUE for timeout, i.e. we cannot
                // overflow. This is ok as elapsedNanos() will never be negative.
                return timeUnit.toNanos(timeout) - elapsedNanos(startNanos)
            }
        }

    /**
     * Returns the amount of time until this timeout expires, in [TimeUnit] units.
     */
    fun getRemainingTime(unit: TimeUnit): Long {
        return if (timeout == INFINITE_TIMEOUT) {
            INFINITE_TIMEOUT
        } else {
            // Using TimeUnit convert() ensures overflows are taken care of
            return unit.convert(remainingNanos, TimeUnit.NANOSECONDS)
        }
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
            "%s, %,.1f msec elapsed",
            remainingTimeoutToString(remainingMills, TimeUnit.MILLISECONDS),
            getElapsedTime(TimeUnit.MILLISECONDS).toDouble()
        )
    }

    /**
     * Throws a [TimeoutException] if this timeout has expired
     */
    @Throws(TimeoutException::class)
    fun throwIfElapsed() {
        if (remainingNanos <= 0) {
            throw TimeoutException("Operation has timed out")
        }
    }

    private fun elapsedNanos(startNanos: Long): Long {
        return nanoTimeProvider.nanoTime() - startNanos
    }

    companion object {

        val INFINITE = TimeoutTracker(INFINITE_TIMEOUT, TimeUnit.NANOSECONDS)

        fun fromTimeout(unit: TimeUnit, timeout: Long): TimeoutTracker {
            return if (timeout == Long.MAX_VALUE)
                INFINITE
            else
                TimeoutTracker(timeout, unit)
        }
    }
}

fun remainingTimeoutToString(timeout: Long, unit: TimeUnit): String {
    return if (timeout == Long.MAX_VALUE) {
        String.format("<INFINITE> msec remaining")
    } else {
        String.format(
            "%,.1f msec remaining",
            TimeUnit.MILLISECONDS.convert(timeout, unit).toDouble()
        )
    }
}
