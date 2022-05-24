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

/**
 * Allows computing a value lazily using a suspending [initializer].
 *
 * Note: The implementation is not thread safe, and [initializer] may be executed
 * multiple times if the first time initialization is executed concurrently.
 */
internal class SuspendingLazy<T>(private val initializer: suspend () -> T) {

    private var _value: T? = null

    suspend fun value(): T {
        return _value ?: init()
    }

    private suspend fun init(): T {
        val result = initializer()
        _value = result
        return result
    }
}
