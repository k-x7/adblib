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

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbChannelReader
import com.android.adblib.read
import com.android.adblib.readExactly
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.adblib.impl.TimeoutTracker
import com.android.adblib.readLines
import com.android.adblib.toChannelReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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
        val host = registerCloseable(TestingAdbSessionHost())
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
        val host = registerCloseable(TestingAdbSessionHost())
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
        val host = registerCloseable(TestingAdbSessionHost())
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
        val host = registerCloseable(TestingAdbSessionHost())
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

    @Test
    fun testChannelReaderWorks() {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, "line 1\nline 2\nline 3\n".toByteArray(Charsets.UTF_8))

        // Act
        val lines = runBlocking {
            channelFactory.openFile(path).use { inputChannel ->
                inputChannel.toChannelReader().use { reader ->
                    reader.toList()
                }
            }
        }

        // Assert
        Assert.assertEquals(3, lines.size)
        Assert.assertEquals("line 1", lines[0])
        Assert.assertEquals("line 2", lines[1])
        Assert.assertEquals("line 3", lines[2])
    }

    @Test
    fun testChannelReaderClosesInputChannel() {
        // Prepare
        val inputChannel = object : AdbInputChannel {
            var closed = false

            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                return -1
            }

            override fun close() {
                closed = true
            }
        }

        // Act
        val list = runBlocking {
            inputChannel.toChannelReader().use { reader ->
                reader.toList()
            }
        }

        // Assert
        Assert.assertEquals(0, list.size)
        Assert.assertEquals(true, inputChannel.closed)
    }

    @Test
    fun testChannelReaderWorksWithCustomNewLines() {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, "line 1\r\r\n\rline 2\r\r\n\rline 3\n".toByteArray(Charsets.UTF_8))

        // Act
        val lines = runBlocking {
            channelFactory.openFile(path).use { inputChannel ->
                inputChannel.toChannelReader(Charsets.UTF_8, "\r\r\n\r", 10).use { reader ->
                    reader.toList()
                }
            }
        }

        // Assert
        Assert.assertEquals(3, lines.size)
        Assert.assertEquals("line 1", lines[0])
        Assert.assertEquals("line 2", lines[1])
        Assert.assertEquals("line 3\n", lines[2])
    }

    @Test
    fun testChannelReaderReportsExceptions() {
        // Prepare
        val inputChannel = object : AdbInputChannel {
            var callCount = 0
            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                if (callCount++ <= 4) {
                    buffer.put("abcd\n".toByteArray(Charsets.UTF_8))
                    return 5
                } else {
                    throw IOException("Failure to read")
                }
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Failure to read")
        runBlocking {
            inputChannel.toChannelReader().use { reader ->
                reader.toList()
            }
        }

        // Assert
        Assert.fail()
    }

    @Test
    fun testReadLinesWorks() {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, "line 1\nline 2\nline 3".toByteArray(Charsets.UTF_8))

        // Act
        val lines = runBlocking {
            channelFactory.openFile(path).use {
                val channel = it.readLines(this, Charsets.UTF_8, "\n", 4)
                channel.toList()
            }
        }

        // Assert
        Assert.assertEquals(3, lines.size)
        Assert.assertEquals("line 1", lines[0])
        Assert.assertEquals("line 2", lines[1])
        Assert.assertEquals("line 3", lines[2])
    }

    @Test
    fun testReadLinesWorksWithCustomNewLines() {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val path = folder.newFile("foo-bar.txt").toPath()
        Files.write(path, "line 1\r\r\n\rline 2\r\r\n\rline 3\n".toByteArray(Charsets.UTF_8))

        // Act
        val lines = runBlocking {
            channelFactory.openFile(path).use {
                val channel = it.readLines(this, Charsets.UTF_8, "\r\r\n\r", 10)
                channel.toList()
            }
        }

        // Assert
        Assert.assertEquals(3, lines.size)
        Assert.assertEquals("line 1", lines[0])
        Assert.assertEquals("line 2", lines[1])
        Assert.assertEquals("line 3\n", lines[2])
    }

    @Test
    fun testReadLinesReportsIOException() {
        // Prepare
        val inputChannel = object : AdbInputChannel {
            var callCount = 0
            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                if (callCount++ <= 4) {
                    buffer.put("abcd\n".toByteArray(Charsets.UTF_8))
                    return 5
                } else {
                    throw IOException("Failure to read")
                }
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Failure to read")
        runBlocking {
            inputChannel.use {
                val channel = it.readLines(this, Charsets.UTF_8, "\n", 256)
                channel.toList()
            }
        }

        // Assert
        Assert.fail()
    }

    @Test
    fun testReadLinesReportsCancellation() {
        // Prepare
        val inputChannel = object : AdbInputChannel {
            var callCount = 0
            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                if (callCount++ <= 4) {
                    buffer.put("abcd\n".toByteArray(Charsets.UTF_8))
                    return 5
                } else {
                    throw CancellationException("Cancelled read")
                }
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Cancelled read")
        runBlocking {
            inputChannel.use {
                val channel = it.readLines(this, Charsets.UTF_8, "\n", 256)
                channel.toList()
            }
        }

        // Assert
        Assert.fail()
    }

    @Test
    fun testReadLinesReportsScopeCancellation() {
        // Prepare
        val inputChannel = object : AdbInputChannel {
            var callCount = 0
            override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                if (callCount++ <= 4) {
                    buffer.put("abcd\n".toByteArray(Charsets.UTF_8))
                    return 5
                } else {
                    delay(5_000)
                    return -1
                }
            }

            override fun close() {
            }
        }

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Hi there!")
        runBlocking {
            inputChannel.use {
                var channel: ReceiveChannel<String>? = null
                val job = launch {
                    channel = it.readLines(this, Charsets.UTF_8, "\n", 256)
                }
                while (channel == null) {
                    delay(5)
                }

                // Cancel the parent scope
                job.cancel(CancellationException("Hi there!"))

                // This should rethrow the cancellation, since the cancellation should have been
                // transferred to the cause of channel closing
                channel!!.toList()
            }
        }

        // Assert
        Assert.fail()
    }

    private suspend fun AdbChannelReader.toList(): List<String> {
        val list = mutableListOf<String>()
        while (true) {
            list.add(this.readLine() ?: break)
        }
        return list
    }
}
