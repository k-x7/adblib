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
package com.android.adblib.testingutils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.time.Duration
import kotlin.math.max
import kotlin.math.min

object CoroutineTestUtils {

    fun <T> runBlockingWithTimeout(
        timeout: Duration = Duration.ofSeconds(30),
        block: suspend CoroutineScope.() -> T
    ): T {
        return runBlocking {
            // The goal with "blockTimeoutException" is to make sure "block" can throw a
            // TimeoutCancellationException to the caller of this method.
            var blockTimeoutException: TimeoutCancellationException?  = null
            try {
                withTimeout(timeout.toMillis()) {
                    try {
                        block(this)
                    } catch(e: TimeoutCancellationException) {
                        blockTimeoutException = e
                        throw e
                    }
                }
            } catch (e: TimeoutCancellationException) {
                blockTimeoutException?.also {
                    // If "block" threw a timeout exception, then rethrow it, as the exception
                    // has nothing to do with our "withTimeout" wrapper
                    throw it
                }
                throw AssertionError(
                    "A test did not terminate within the specified timeout ($timeout), " +
                            "there is a bug somewhere (in the test or in the tested code)", e
                )
            }
        }
    }

    suspend fun yieldUntil(
        timeout: Duration = Duration.ofSeconds(30),
        predicate: suspend () -> Boolean
    ) {
        try {
            withTimeout(timeout.toMillis()) {
                while (!predicate()) {
                    delayForTimeout(timeout)
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "A yieldUntil condition was not satisfied within the specified timeout " +
                        "($timeout), there is a bug somewhere (in the test or in the tested code)", e
            )
        }
    }

    suspend fun <T> waitNonNull(
        timeout: Duration = Duration.ofSeconds(30),
        provider: suspend () -> T?
    ): T {
        suspend fun <T> loop(provider: suspend () -> T?): T {
            while (true) {
                val value = provider()
                if (value != null) {
                    return value
                }
                delayForTimeout(timeout)
            }
        }

        return try {
            withTimeout(timeout.toMillis()) {
                loop(provider)
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "A waitNonNull condition was not satisfied within the specified timeout " +
                        "($timeout), there is a bug somewhere (in the test or in the tested code)", e
            )
        }
    }

    private suspend fun delayForTimeout(timeout: Duration) {
        // Delay between 1 and 100 millis
        delay(min(100, max(1, timeout.toMillis() / 20)))
    }
}
