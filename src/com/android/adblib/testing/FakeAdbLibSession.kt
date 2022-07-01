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

import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.impl.CoroutineScopeCacheImpl
import com.android.adblib.impl.channels.AdbChannelFactoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * A fake implementation of [FakeAdbSession] for tests.
 */
class FakeAdbSession : AdbSession {

    override val scope = CoroutineScope(Dispatchers.IO)

    override val cache: CoroutineScopeCache = CoroutineScopeCacheImpl(scope)

    override fun throwIfClosed() {
        // Not yet implemented
    }

    override val hostServices = FakeAdbHostServices(this)

    override val deviceServices = FakeAdbDeviceServices(this)

    override val host: AdbSessionHost = FakeAdbSessionHost()

    override val channelFactory: AdbChannelFactory = AdbChannelFactoryImpl(host)

    override fun close() {
        (cache as CoroutineScopeCacheImpl).close()
    }
}
