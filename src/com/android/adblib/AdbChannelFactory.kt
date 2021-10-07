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
package com.android.adblib

import java.io.IOException
import java.nio.file.Path

/**
 * Factory class for various implementations of [AdbInputChannel] and [AdbOutputChannel]
 */
interface AdbChannelFactory {

    /**
     * Opens an existing file, and returns an [AdbInputChannel] for reading from it.
     *
     * @throws IOException if the operation fails
     */
    suspend fun openFile(path: Path): AdbInputChannel

    /**
     * Creates a new file, or truncates an existing file, and returns an [AdbOutputChannel]
     * for writing to it.
     *
     * @throws IOException if the operation fails
     */
    suspend fun createFile(path: Path): AdbOutputChannel

    /**
     * Creates a new file that does not already exists on disk, and returns an [AdbOutputChannel]
     * for writing to it.
     *
     * @throws IOException if the operation fails or if the file already exists on disk
     */
    suspend fun createNewFile(path: Path): AdbOutputChannel
}
