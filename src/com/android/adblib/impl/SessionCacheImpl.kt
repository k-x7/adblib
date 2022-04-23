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
package com.android.adblib.impl

import com.android.adblib.SessionCache
import com.android.adblib.utils.SuppressedExceptions
import java.util.concurrent.ConcurrentHashMap

internal class SessionCacheImpl : SessionCache, AutoCloseable {

    private val map = ConcurrentHashMap<SessionCache.Key<*>, Any>()

    override fun <T> getOrPut(key: SessionCache.Key<T>, defaultValue: () -> T): T {
        val result = map.getOrPut(key) { defaultValue() }
        @Suppress("UNCHECKED_CAST")
        return (result as T)
    }

    override fun close() {
        val toClose = map.values.filterIsInstance<AutoCloseable>()
        map.clear()
        val closeExceptions = SuppressedExceptions.init()
        toClose.forEach {
            kotlin.runCatching {
                it.close()
            }.onFailure {
              SuppressedExceptions.add(closeExceptions, it)
            }
        }
        if (closeExceptions.isNotEmpty()) {
            val error = Exception("One or more errors closing elements of cache")
            closeExceptions.forEach { error.addSuppressed(it) }
            throw error
        }
    }
}
