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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.time.Duration

object CoroutineTestUtils {

    fun <T> runBlockingWithTimeout(
        timeout: Duration = Duration.ofSeconds(10),
        block: suspend CoroutineScope.() -> T
    ): T {
        return runBlocking {
            try {
                withTimeout(timeout.toMillis()) {
                    block(this)
                }
            } catch (e: TimeoutCancellationException) {
                throw AssertionError(
                    "A test did not terminate within the specified timeout, " +
                            "there is a bug somewhere (in the test or in the tested code)", e
                )
            }
        }
    }

    suspend fun yieldUntil(
        timeout: Duration = Duration.ofSeconds(5),
        predicate: suspend () -> Boolean
    ) {
        try {
            withTimeout(timeout.toMillis()) {
                while (!predicate()) {
                    yield()
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw AssertionError(
                "A yieldUntil condition was not satisfied within " +
                        "5 seconds, there is a bug somewhere (in the test or in the tested code)", e
            )
        }
    }
}
