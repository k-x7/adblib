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

import com.android.adblib.AdbLogger
import com.android.adblib.AdbLoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit

class TestingAdbLoggerFactory : AdbLoggerFactory {

    var minLevel: AdbLogger.Level = AdbLogger.Level.WARN

    override val logger: AdbLogger by lazy {
        TestingAdbLogger(this)
    }

    override fun createLogger(cls: Class<*>): AdbLogger {
        return createLogger(cls.simpleName)
    }

    override fun createLogger(category: String): AdbLogger {
        return TestingAdbLoggerWithPrefix(this, category)
    }
}

open class TestingAdbLogger(
    private val factory: TestingAdbLoggerFactory,
    var logDeltaTime: Boolean = true
) : AdbLogger() {

    open val prefix: String = "adblib"

    private var previousInstant: Instant? = null

    private val threadNameWidth = 35

    override val minLevel: Level
        get() = factory.minLevel

    override fun log(level: Level, message: String) {
        if (logDeltaTime) {
            val newInstant = Instant.now()
            synchronized(this) {
                val prevInstant = previousInstant ?: newInstant
                previousInstant = newInstant
                println(
                    String.format(
                        "[%s%s] [%-${threadNameWidth}s] %7s - %30s - %s",
                        formatInstant(newInstant),
                        if (logDeltaTime) deltaInstant(newInstant, prevInstant) else "",
                        Thread.currentThread().name.takeLast(threadNameWidth),
                        level.toString().takeLast(7),
                        prefix.takeLast(30),
                        message
                    )
                )
            }
        } else {
            println(
                String.format(
                    "[%-${threadNameWidth}s] %7s - %30s - %s",
                    Thread.currentThread().name.takeLast(threadNameWidth),
                    level.toString().takeLast(7),
                    prefix.takeLast(30),
                    message
                )
            )
        }
    }

    private fun deltaInstant(newInstant: Instant, prevInstant: Instant): String {
        // We want to log xxx.y "milliseconds"
        val micros = ChronoUnit.MICROS.between(prevInstant, newInstant) / 100
        return if (micros > 0L) {
            val microsString = String.format("%4s", micros)
            String.format("(+%s.%sms)", microsString.substring(0, 3), microsString.substring(3, 4))
        } else {
            "          "
        }
    }

    private fun formatInstant(newInstant: Instant) =
        newInstant.toString().replace('T', ' ').dropLast(4)

    override fun log(level: Level, exception: Throwable?, message: String) {
        if (level >= minLevel) {
            log(level, message)
            exception?.printStackTrace()
        }
    }
}

class TestingAdbLoggerWithPrefix(
    factory: TestingAdbLoggerFactory,
    override val prefix: String
) : TestingAdbLogger(factory)
