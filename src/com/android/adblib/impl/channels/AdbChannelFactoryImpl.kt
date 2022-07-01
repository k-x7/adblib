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
package com.android.adblib.impl.channels

import com.android.adblib.AdbChannelFactory
import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.withContext
import java.nio.channels.AsynchronousFileChannel
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

internal class AdbChannelFactoryImpl(private val host: AdbSessionHost) : AdbChannelFactory {

    override suspend fun openFile(path: Path): AdbInputChannel {
        return openInput(path, StandardOpenOption.READ)
    }

    /**
     * Creates an [AdbOutputFileChannel] for a new file which does not already exist
     */
    override suspend fun createNewFile(path: Path): AdbOutputChannel {
        return openOutput(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    }

    /**
     * Creates an [AdbOutputFileChannel] for a new file, optionally truncating the existing one
     */
    override suspend fun createFile(path: Path): AdbOutputChannel {
        return openOutput(
          path,
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING,
          StandardOpenOption.WRITE
        )
    }

    private suspend fun openOutput(
      path: Path,
      vararg options: OpenOption
    ): AdbOutputFileChannel {
        return withContext(host.ioDispatcher) {
          @Suppress("BlockingMethodInNonBlockingContext")
          val fileChannel = AsynchronousFileChannel.open(path, *options)
          fileChannel.closeOnException {
            AdbOutputFileChannel(host, path, fileChannel)
          }
        }
    }

    private suspend fun openInput(
      path: Path,
      vararg options: OpenOption
    ): AdbInputFileChannel {
        return withContext(host.ioDispatcher) {
          @Suppress("BlockingMethodInNonBlockingContext")
          val fileChannel = AsynchronousFileChannel.open(path, *options)
          fileChannel.closeOnException {
            AdbInputFileChannel(host, path, fileChannel)
          }
        }
    }
}
