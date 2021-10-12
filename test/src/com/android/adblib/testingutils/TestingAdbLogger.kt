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
import java.time.Instant
import java.time.temporal.ChronoUnit

class TestingAdbLogger(
    var minLevel: Level = Level.VERBOSE,
    var logDeltaTime: Boolean = true
) : AdbLogger() {

    private var previousInstant: Instant? = null

    override fun log(level: Level, message: String) {
        if (level >= minLevel) {
            if (logDeltaTime) {
                val newInstant = Instant.now()
                synchronized(this) {
                    val prevInstant = previousInstant ?: newInstant
                    previousInstant = newInstant

                    println(
                        String.format(
                            "[%s%s] [adblib] [%-40s] %s: %s",
                            formatInstant(newInstant),
                            deltaInstant(newInstant, prevInstant),
                            Thread.currentThread().name,
                            level,
                            message
                        )
                    )
                }
            } else {
                println(String.format("[adblib] [%-40s] %s: %s", Thread.currentThread().name, level, message))
            }
        }
    }

    private fun deltaInstant(newInstant: Instant, prevInstant: Instant): String {
        // We want to log xxx.y "milliseconds"
        val micros = ChronoUnit.MICROS.between(prevInstant, newInstant) / 100
        return if (micros > 0L) {
            val microsString = String.format("%4s", micros)//.replace(' ', '0')
            String.format("(+%s.%sms)", microsString.substring(0, 3), microsString.substring(3, 4))
        } else {
            "          "
        }
    }

    private fun formatInstant(newInstant: Instant) =
        newInstant.toString().replace('T', ' ').dropLast(4)

    override fun log(level: Level, exception: Throwable, message: String) {
        if (level >= minLevel) {
            log(level, message)
            exception.printStackTrace()
        }
    }
}
