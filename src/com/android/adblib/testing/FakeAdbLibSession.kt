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
import com.android.adblib.AdbLibHost
import com.android.adblib.AdbLibSession

/**
 * A fake implementation of [FakeAdbLibSession] for tests.
 */
class FakeAdbLibSession : AdbLibSession {
  override val hostServices = FakeAdbHostServices(this)

  override val deviceServices = FakeAdbDeviceServices(this)


  override val host: AdbLibHost
    get() = TODO("Not yet implemented")

  override val channelFactory: AdbChannelFactory
    get() = TODO("Not yet implemented")

  override fun close() {
    TODO("Not yet implemented")
  }
}