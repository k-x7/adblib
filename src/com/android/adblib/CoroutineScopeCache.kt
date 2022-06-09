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

import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentMap

/**
 * A thread safe in-memory cache of [Key&lt;T&gt;][Key] to `T` values whose lifetime is tied
 * to a [CoroutineScope].
 *
 * Values can optionally implement [AutoCloseable], in which case these values are
 * [closed][AutoCloseable.close] when removed from the cache or when the cache is
 * closed.
 */
interface CoroutineScopeCache {

    /**
     * The scope that defines the lifecycle of this cache, i.e. if the [scope] is
     * cancelled, the cache is cleared and [AutoCloseable.close] is called
     * on all values implementing [AutoCloseable].
     */
    val scope: CoroutineScope

    /**
     * Returns the value for the given [key]. If the key is not found in the map,
     * calls the [defaultValue] function, puts its result into the map under the
     * given key and returns it.
     *
     * This method guarantees not to put the value into the map if the key is
     * already there, but the [defaultValue] function may be invoked even if
     * the key is already in the map.
     *
     * **Note**: [getOrPut] and [getOrPutSuspending] use separate in-memory caches
     * internally to prevent conflicting behavior between suspending and
     * non-suspending computations.

     * @see [ConcurrentMap.getOrPut]
     *
     */
    fun <T> getOrPut(key: Key<T>, defaultValue: () -> T): T

    /**
     * Suspending version of [getOrPut]: returns the value for the given [key].
     * If the key is not found in the map, calls the [defaultValue] coroutine,
     * puts its result into the map under the given key and returns it.
     *
     * Unlike [getOrPut], this method guarantees that [defaultValue] is invoked
     * at most once if the key is not already present in the map. This implies that,
     * for a given [key], the first caller gets to compute the value stored in the
     * map, and follow-up callers are suspended until the value is computed.
     *
     * Also unlike [getOrPut], if [defaultValue] throws an exception for a given [key],
     * the same exception will be re-thrown for all subsequent callers.
     *
     * **Note**: [getOrPut] and [getOrPutSuspending] use separate in-memory caches
     * internally to prevent conflicting behavior between suspending and
     * non-suspending computations.
     */
    suspend fun <T> getOrPutSuspending(
        key: Key<T>,
        defaultValue: suspend CoroutineScope.() -> T
    ): T

    /**
     * Suspending version of [getOrPut]: returns the value for the given [key].
     * If the key is not found in the map, asynchronously starts evaluating the
     * [defaultValue] coroutine, then immediately returns [fastDefaultValue].
     * Once the [defaultValue] coroutine completes, the resulting value is stored in
     * the map.
     *
     * Unlike [getOrPut], this method guarantees that [defaultValue] is invoked
     * at most once if the key is not already present in the map. This implies that,
     * for a given [key], the first caller gets to compute the value stored in the
     * map, and follow-up callers get [fastDefaultValue] until the value is computed.
     *
     * Also unlike [getOrPut], if [defaultValue] throws an exception for a given [key],
     * the same exception will be re-thrown for all subsequent callers.
     *
     * **Note**: [getOrPut] and [getOrPutSuspending] use separate in-memory caches
     * internally to prevent conflicting behavior between suspending and
     * non-suspending computations.
     */
    fun <T> getOrPutSuspending(
        key: Key<T>,
        fastDefaultValue: () -> T,
        defaultValue: suspend CoroutineScope.() -> T
    ): T

    /**
     * Key type for the [CoroutineScopeCache]. Keys should implement [equals] and [hashCode].
     */
    open class Key<T>(
        /**
         * Friendly name of the key, does not need to be an identifier.
         */
        val name: String
    )
}
