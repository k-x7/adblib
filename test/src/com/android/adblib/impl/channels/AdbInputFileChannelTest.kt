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

import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbLibHost
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files

class AdbInputFileChannelTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

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
    fun testReadWorks() {
        // Prepare
        val host = registerCloseable(TestingAdbLibHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, ByteArray(20))

        // Act
        val count = runBlocking {
            channelFactory.openFile(path).use {
                val buffer = ByteBuffer.allocate(5)
                it.read(buffer, TimeoutTracker.INFINITE)
            }
        }

        // Assert
        Assert.assertEquals(5, count)
        Assert.assertTrue(Files.exists(path))
        Assert.assertEquals(20, Files.size(path))
    }

    @Test
    fun testReadReturnsMinusOneOnEOF() {
        // Prepare
        val host = registerCloseable(TestingAdbLibHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, ByteArray(5))

        // Act
        val count = runBlocking {
            channelFactory.openFile(path).use {
                val buffer = ByteBuffer.allocate(5)
                it.read(buffer, TimeoutTracker.INFINITE)
                buffer.flip()
                it.read(buffer, TimeoutTracker.INFINITE)
            }
        }

        // Assert
        Assert.assertEquals(-1, count)
        Assert.assertTrue(Files.exists(path))
        Assert.assertEquals(5, Files.size(path))
    }

    @Test
    fun testReadExactlyWorks() {
        // Prepare
        val host = registerCloseable(TestingAdbLibHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, ByteArray(20))

        // Act
        val count = runBlocking {
            channelFactory.openFile(path).use {
                val buffer = ByteBuffer.allocate(20)
                it.readExactly(buffer, TimeoutTracker.INFINITE)
                buffer.flip()
                buffer.remaining()
            }
        }

        // Assert
        Assert.assertEquals(20, count)
        Assert.assertTrue(Files.exists(path))
        Assert.assertEquals(20, Files.size(path))
    }

    @Test
    fun testReadExactlyThrowsIfFileTooShort() {
        // Prepare
        val host = registerCloseable(TestingAdbLibHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, ByteArray(20))

        // Act
        exceptionRule.expect(IOException::class.java)
        /*val count =*/ runBlocking {
            channelFactory.openFile(path).use {
                val buffer = ByteBuffer.allocate(100)
                it.readExactly(buffer, TimeoutTracker.INFINITE)
            }
        }

        // Assert
        Assert.fail() // Should not be reached
    }
}
