package com.android.adblib.utils

import com.android.adblib.SystemNanoTimeProvider
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class TimeoutTrackerTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @Test
    fun testConstructorSetsCorrectInitialValues() {
        // Prepare
        val nanoProvider = TestNanoProvider()
        val timeout = TimeoutTracker(nanoProvider, 1, TimeUnit.SECONDS)

        // Act (nothing, ensures tracker is initialized correctly)

        // Assert
        Assert.assertEquals(1, timeout.remainingTime)
        Assert.assertEquals(TimeUnit.SECONDS, timeout.timeUnit)
        Assert.assertEquals(1_000, timeout.getRemainingTime(TimeUnit.MILLISECONDS))
        Assert.assertEquals(0, timeout.getElapsedTime(TimeUnit.SECONDS))
        // Should not throw
        timeout.throwIfElapsed()
    }

    @Test
    fun testAdvancingTimeIsTracked() {
        // Prepare
        val nanoProvider = TestNanoProvider()
        val timeout = TimeoutTracker(nanoProvider, 1_000, TimeUnit.MILLISECONDS)

        // Act (nothing, ensures tracker is initialize correctly)
        nanoProvider.advance(400, TimeUnit.MILLISECONDS)

        // Assert
        Assert.assertEquals(600, timeout.remainingTime)
        Assert.assertEquals(TimeUnit.MILLISECONDS, timeout.timeUnit)
        Assert.assertEquals(600_000, timeout.getRemainingTime(TimeUnit.MICROSECONDS))
        Assert.assertEquals(400_000, timeout.getElapsedTime(TimeUnit.MICROSECONDS))
        // Should not throw
        timeout.throwIfElapsed()
    }

    @Test
    fun testExceptionIsThrownWhenTimeoutExpires() {
        // Prepare
        val nanoProvider = TestNanoProvider()
        val timeout = TimeoutTracker(nanoProvider, 1_000, TimeUnit.MILLISECONDS)

        // Act (nothing, ensures tracker is initialize correctly)
        nanoProvider.advance(1_001, TimeUnit.MILLISECONDS)

        // Assert
        Assert.assertEquals(-1, timeout.remainingTime)
        Assert.assertEquals(TimeUnit.MILLISECONDS, timeout.timeUnit)
        Assert.assertEquals(-1_000, timeout.getRemainingTime(TimeUnit.MICROSECONDS))
        Assert.assertEquals(1_001_000, timeout.getElapsedTime(TimeUnit.MICROSECONDS))
        exceptionRule.expect(TimeoutException::class.java)
        timeout.throwIfElapsed()
    }

    private class TestNanoProvider : SystemNanoTimeProvider() {

        private var currentTimeNano: Long = 1_000_000

        override fun nanoTime(): Long {
            return currentTimeNano
        }

        fun advance(time: Long, unit: TimeUnit) {
            currentTimeNano += TimeUnit.NANOSECONDS.convert(time, unit)
        }
    }
}
