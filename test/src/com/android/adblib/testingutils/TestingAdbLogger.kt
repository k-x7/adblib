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

class TestingAdbLogger(private val minLevel: Level = Level.VERBOSE) : AdbLogger() {
    override fun log(level: Level, message: String) {
        if (level >= minLevel) {
            println(String.format("[adblib] [%-40s] %s: %s",Thread.currentThread().name, level, message))
        }
    }

    override fun log(level: Level, exception: Throwable, message: String) {
        if (level >= minLevel) {
            log(level, message)
            exception.printStackTrace()
        }
    }
}
