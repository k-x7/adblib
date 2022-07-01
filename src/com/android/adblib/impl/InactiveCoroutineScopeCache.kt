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

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevicesTracker
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.deviceCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.CancellationException

/**
 * A [CoroutineScopeCache] that is always empty and exposes a "cancelled" [scope].
 * This is useful to expose a "no-op" cache for a disconnected device.
 *
 * @see AdbSession.deviceCache
 * @see ConnectedDevicesTracker.deviceCache
 */
internal object InactiveCoroutineScopeCache : CoroutineScopeCache {

    val job: Job = SupervisorJob().also {
        it.cancel(CancellationException("This device cache is inactive"))
    }

    override val scope = CoroutineScope(job)

    override fun <T> getOrPut(key: CoroutineScopeCache.Key<T>, defaultValue: () -> T): T {
        return defaultValue()
    }

    override suspend fun <T> getOrPutSuspending(
        key: CoroutineScopeCache.Key<T>,
        defaultValue: suspend CoroutineScope.() -> T
    ): T {
        return scope.defaultValue()
    }

    override fun <T> getOrPutSuspending(
        key: CoroutineScopeCache.Key<T>,
        fastDefaultValue: () -> T,
        defaultValue: suspend CoroutineScope.() -> T
    ): T {
        return fastDefaultValue()
    }
}
