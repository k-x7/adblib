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

import com.android.adblib.writeExactly
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.testingutils.TestingAdbSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.ByteBuffer
import java.nio.file.Files

class AdbOutputFileChannelTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    val folder = TemporaryFolder()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun testWriteToCreateNew() {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.delete(path) // Delete file, since we are about to create a new one

        // Act
        val count = runBlocking {
            channelFactory.createNewFile(path).use {
                val buffer = ByteBuffer.allocate(10)
                buffer.putInt(10)
                buffer.flip()
                it.writeExactly(buffer, TimeoutTracker.INFINITE)
                buffer.flip()
                buffer.remaining()
            }
        }

        // Assert
        Assert.assertEquals(4, count)
        Assert.assertTrue(Files.exists(path))
        Assert.assertEquals(4, Files.size(path))
    }

    @Test
    fun testWriteToCreate() {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val path = folder.newFile("foo-bar.txt").toPath()

        // Act
        val count = runBlocking {
            channelFactory.createFile(path).use {
                val buffer = ByteBuffer.allocate(10)
                buffer.putInt(10)
                buffer.flip()
                it.writeExactly(buffer, TimeoutTracker.INFINITE)
                buffer.flip()
                buffer.remaining()
            }
        }

        // Assert
        Assert.assertEquals(4, count)
        Assert.assertTrue(Files.exists(path))
        Assert.assertEquals(4, Files.size(path))
    }
}
