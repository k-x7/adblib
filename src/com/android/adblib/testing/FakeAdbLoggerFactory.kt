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
package com.android.adblib.testing

import com.android.adblib.AdbLogger
import com.android.adblib.AdbLoggerFactory
import java.util.Collections

class FakeAdbLoggerFactory : AdbLoggerFactory {
    private val logEntries = Collections.synchronizedList(ArrayList<FakeAdbLoggerEntry>())

    var minLevel = AdbLogger.Level.VERBOSE

    var printToStdout: Boolean = true

    override val logger: AdbLogger = FakeAdbLogger(this, "fakeadblib")

    override fun createLogger(cls: Class<*>): AdbLogger {
        return createLogger(cls.name)
    }

    override fun createLogger(category: String): AdbLogger {
        return FakeAdbLogger(this, category)
    }

    fun addEntry(entry: FakeAdbLoggerEntry) {
        logEntries.add(entry)
        if (printToStdout) {
            val exceptionSuffix = entry.exception?.let { "- throwable=${entry.exception}" } ?: ""
            println("[${entry.logger.name}] ${entry.level} - ${entry.message}$exceptionSuffix")
        }
    }
}

class FakeAdbLogger(val factory: FakeAdbLoggerFactory, val name: String) : AdbLogger() {

    override val minLevel: Level
        get() = factory.minLevel

    override fun log(level: Level, message: String) {
        log(level, null, message)
    }

    override fun log(level: Level, exception: Throwable?, message: String) {
        factory.addEntry(FakeAdbLoggerEntry(this, level, exception, message))
    }
}

data class FakeAdbLoggerEntry(val logger: FakeAdbLogger, val level: AdbLogger.Level, val exception: Throwable?, val message: String)
