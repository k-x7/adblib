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

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSession
import com.android.adblib.impl.channels.AdbInputStreamChannel
import com.android.adblib.impl.channels.DEFAULT_CHANNEL_BUFFER_SIZE
import com.android.adblib.utils.AdbProtocolUtils
import java.io.InputStream

fun String.asAdbInputChannel(
  session: AdbSession,
  bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
): AdbInputChannel {
    //TODO: This is inefficient as `byteInputStream` creates an in-memory copy of the whole string
    return byteInputStream(AdbProtocolUtils.ADB_CHARSET).asAdbInputChannel(session, bufferSize)
}

fun InputStream.asAdbInputChannel(
  session: AdbSession,
  bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
): AdbInputChannel {
    return AdbInputStreamChannel(session.host, this, bufferSize)
}
