/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbLogger
import com.android.adblib.impl.TimeoutTracker
import kotlinx.coroutines.CoroutineExceptionHandler
import java.time.Instant
import java.util.concurrent.TimeUnit

class TestingAdbSessionHost : AdbSessionHost() {

    val uncaughtExceptions = mutableListOf<Throwable>()
    var overrideUtcNow: Instant? = null

    override val loggerFactory: TestingAdbLoggerFactory by lazy {
        TestingAdbLoggerFactory()
    }

    override fun utcNow(): Instant {
        return overrideUtcNow ?: Instant.now()
    }

    override val parentContext =
        CoroutineExceptionHandler { ctx, exception ->
            uncaughtExceptions.add(exception)
            logger.error(exception, "Unhandled exception in $ctx")
        }

    override fun close() {
        logger.debug { "Testing AbbListHost closed" }
    }

    internal fun newTimeout(timeout: Long, unit: TimeUnit): TimeoutTracker {
        return TimeoutTracker(timeProvider, timeout, unit)
    }
}

/**
 * Overrides the default [AdbLogger.Level] of the [TestingAdbSessionHost], useful
 * for enabling more verbose logging for a single test, e.g.
 *
 * `hostServices.session.host.setTestLoggerMinLevel(AdbLogger.Level.VERBOSE)`
 */
@Suppress("unused")
fun AdbSessionHost.setTestLoggerMinLevel(level: AdbLogger.Level) {
    (this as TestingAdbSessionHost).loggerFactory.minLevel = level
}
