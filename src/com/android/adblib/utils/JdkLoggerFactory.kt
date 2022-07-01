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
package com.android.adblib.utils

import com.android.adblib.AdbLogger
import com.android.adblib.AdbLoggerFactory
import java.util.logging.Logger

/**
 * An implementation of [AdbLoggerFactory] based on the built-in JDK [Logger] class.
 */
class JdkLoggerFactory : AdbLoggerFactory {

    override val logger: AdbLogger = JdkLogger("com.android.adblib")

    override fun createLogger(cls: Class<*>): AdbLogger {
        return JdkLogger(cls::class.java.name)
    }

    override fun createLogger(category: String): AdbLogger {
        return JdkLogger(category)
    }

    class JdkLogger(name: String) : AdbLogger() {

        val logger: Logger = Logger.getLogger(name)

        override val minLevel: Level
            get() = when {
                logger.isLoggable(java.util.logging.Level.ALL) -> Level.VERBOSE
                logger.isLoggable(java.util.logging.Level.FINE) -> Level.DEBUG
                logger.isLoggable(java.util.logging.Level.INFO) -> Level.INFO
                logger.isLoggable(java.util.logging.Level.WARNING) -> Level.WARN
                else -> Level.ERROR
            }

        override fun log(level: Level, message: String) {
            logger.log(mapLevel(level), message)
        }

        override fun log(level: Level, exception: Throwable?, message: String) {
            logger.log(mapLevel(level), exception) { message }
        }

        private fun mapLevel(level: Level): java.util.logging.Level {
            return when (level) {
                Level.VERBOSE -> java.util.logging.Level.ALL
                Level.DEBUG -> java.util.logging.Level.FINE
                Level.INFO -> java.util.logging.Level.INFO
                Level.WARN -> java.util.logging.Level.WARNING
                Level.ERROR -> java.util.logging.Level.SEVERE
            }
        }
    }
}
