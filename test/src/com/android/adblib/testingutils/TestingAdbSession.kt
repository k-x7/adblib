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
package com.android.adblib.testingutils

import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbDeviceServices
import com.android.adblib.AdbHostServices
import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.impl.channels.AdbChannelFactoryImpl
import kotlinx.coroutines.CoroutineScope

class TestingAdbSession : AdbSession {

    override val host: AdbSessionHost = TestingAdbSessionHost()

    override val channelFactory: AdbChannelFactory = AdbChannelFactoryImpl(this)

    override val hostServices: AdbHostServices
        get() = todo()

    override val deviceServices: AdbDeviceServices
        get() = todo()

    override val scope: CoroutineScope
        get() = todo()

    override val cache: CoroutineScopeCache
        get() = todo()

    override fun throwIfClosed() {
        todo()
    }

    override fun close() {
    }

    private fun todo(): Nothing {
        TODO("This test class is for wrapping AdbSessionHost only.  " +
                     "Use FakeAdbSession instead for additional functionality.")
    }
}
