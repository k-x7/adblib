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
package com.android.adblib

import java.util.concurrent.ConcurrentMap

/**
 * A thread safe in-memory cache of [Key] to [Any] value.
 *
 * Values can optionally implement [AutoCloseable], in which case these values are
 * [closed][AutoCloseable.close] when removed from the cache or when the cache is
 * closed.
 */
interface SessionCache : AutoCloseable {

    /**
     * Concurrent getOrPut, see [ConcurrentMap.getOrPut]
     *
     * Returns the value for the given [key]. If the key is not found in the map,
     * calls the [defaultValue] function, puts its result into the map under the
     * given key and returns it.
     *
     * This method guarantees not to put the value into the map if the key is
     * already there, but the [defaultValue] function may be invoked even if
     * the key is already in the map.
     */
    fun <T> getOrPut(key: Key<T>, defaultValue: () -> T): T

    /**
     * Key type for the [SessionCache]. Keys should implement [equals] and [hashCode].
     */
    open class Key<T>(
        /**
         * Friendly name of the key, does not need to be an identifier.
         */
        val name: String
    )
}
