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
package com.android.adblib

import org.junit.Assert
import org.junit.Test
import java.io.IOException

class AdbLoggerTest {

    @Test
    fun simpleLoggerFunctionsWork() {
        // Prepare
        val testLogger = MyAdbLogger()

        // Act
        testLogger.debug { "foo" }
        testLogger.error(IOException(), "bar")
        testLogger.log(AdbLogger.Level.ERROR, "foo2bar")
        testLogger.log(AdbLogger.Level.ERROR, RuntimeException(), "foo3bar")

        // Assert
        Assert.assertEquals(4, testLogger.entries.size)

        Assert.assertEquals("foo", testLogger.entries[0].message)
        Assert.assertEquals(AdbLogger.Level.DEBUG, testLogger.entries[0].level)
        Assert.assertNull(testLogger.entries[0].exception)

        Assert.assertEquals("bar", testLogger.entries[1].message)
        Assert.assertEquals(AdbLogger.Level.ERROR, testLogger.entries[1].level)
        Assert.assertTrue(testLogger.entries[1].exception is IOException)

        Assert.assertEquals("foo2bar", testLogger.entries[2].message)
        Assert.assertEquals(AdbLogger.Level.ERROR, testLogger.entries[2].level)
        Assert.assertNull(testLogger.entries[2].exception)

        Assert.assertEquals("foo3bar", testLogger.entries[3].message)
        Assert.assertEquals(AdbLogger.Level.ERROR, testLogger.entries[3].level)
        Assert.assertTrue(testLogger.entries[3].exception is RuntimeException)
    }

    @Test
    fun allLoggerFunctionsWork() {
        // Prepare
        val loggerFunctions: List<LoggerOverloads> = listOf(
            LoggerOverloads(
                AdbLogger.Level.VERBOSE,
                { s -> verbose { s } },
                { e, s -> verbose(e) { s } },
            ),
            LoggerOverloads(
                AdbLogger.Level.DEBUG,
                { s -> debug { s } },
                { e, s -> debug(e) { s } },
            ),
            LoggerOverloads(
                AdbLogger.Level.INFO,
                { s -> info { s } },
                { e, s -> info(e) { s } },
            ),
            LoggerOverloads(
                AdbLogger.Level.WARN,
                AdbLogger::warn,
                AdbLogger::warn,
            ),
            LoggerOverloads(
                AdbLogger.Level.ERROR,
                AdbLogger::error,
                AdbLogger::error,
            ),
        )

        val testLogger = MyAdbLogger()

        // Act
        loggerFunctions.forEach { loggerOverloads ->
            loggerOverloads.fun1.invoke(testLogger, "foo")
            loggerOverloads.fun2.invoke(testLogger, IOException(), "foo3")
        }

        // Assert
        Assert.assertEquals(2 * loggerFunctions.size, testLogger.entries.size)

        var index = 0
        loggerFunctions.forEach { loggerOverloads ->
            Assert.assertEquals("foo", testLogger.entries[index].message)
            Assert.assertEquals(loggerOverloads.level, testLogger.entries[index].level)
            Assert.assertNull(testLogger.entries[index].exception)
            index++

            Assert.assertEquals("foo3", testLogger.entries[index].message)
            Assert.assertEquals(loggerOverloads.level, testLogger.entries[index].level)
            Assert.assertTrue(testLogger.entries[index].exception is IOException)
            index++
        }
    }

    class MyAdbLogger : AdbLogger() {

        val entries = ArrayList<Entry>()

        override fun log(level: Level, message: String) {
            entries.add(Entry(level, message, null))
        }

        override fun log(level: Level, exception: Throwable, message: String) {
            entries.add(Entry(level, message, exception))
        }

        override val minLevel: Level
            get() = Level.VERBOSE

        data class Entry(val level: Level, val message: String, val exception: Throwable?)
    }

    open class LoggerOverloads(
        val level: AdbLogger.Level,
        val fun1: AdbLogger.(String) -> Unit,
        val fun2: AdbLogger.(exception: Throwable, message: String) -> Unit
    )
}
