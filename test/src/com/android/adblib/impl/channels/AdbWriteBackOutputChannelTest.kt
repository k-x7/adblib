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

import com.android.adblib.AdbOutputChannel
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.TestingAdbSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class AdbWriteBackOutputChannelTest {

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun writeBackWorks(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val output = object: AdbOutputChannel {

            var closed: Boolean = false
                private set

            var length: Int = 0
                private set

            override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                val count = buffer.remaining()
                repeat(count) {
                    buffer.get()
                }
                length += count
                return length
            }

            override fun close() {
                closed = true
            }
        }

        val writeBackChannel = channelFactory.createWriteBackChannel(output, 1_000)

        // Act
        val counts = (0 until 10)
            .map {
                val buffer = ByteBuffer.allocate(100)
                writeBackChannel.write(buffer)
            }.toList()
        writeBackChannel.shutdown()
        writeBackChannel.close()

        // Assert
        Assert.assertEquals(10, counts.size)
        repeat(counts.size) { index ->
            Assert.assertEquals(100, counts[index])
        }
        Assert.assertEquals(1_000, output.length)
        Assert.assertEquals(true, output.closed)
    }

    @Test
    fun writeBackRethrowsException(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val output = object: AdbOutputChannel {

            var callCount = 0

            var closed: Boolean = false
                private set

            var length: Int = 0
                private set

            override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                callCount++
                if (callCount == 3) {
                    throw IOException("Fake I/O error")
                }
                val count = buffer.remaining()
                repeat(count) {
                    buffer.get()
                }
                length += count
                return length
            }

            override fun close() {
                closed = true
            }
        }

        val writeBackChannel = channelFactory.createWriteBackChannel(output, 1_000)

        // Act
        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Fake I/O error")
        repeat(10) {
            val buffer = ByteBuffer.allocate(100)
            writeBackChannel.write(buffer)
            delay(10)
        }
        writeBackChannel.shutdown()
        writeBackChannel.close()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun writeBackRethrowsExceptionOnShutdown(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val writeMutex = Mutex()
        writeMutex.lock()
        val output = object: AdbOutputChannel {
            override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                writeMutex.lock() // Wait for "shutdown" to be called
                throw IOException("Fake I/O error")
            }

            override fun close() {
            }
        }

        val writeBackChannel = channelFactory.createWriteBackChannel(output, 1_000)

        // Act
        val buffer = ByteBuffer.allocate(10)
        writeBackChannel.write(buffer)

        exceptionRule.expect(IOException::class.java)
        exceptionRule.expectMessage("Fake I/O error")
        writeMutex.unlock() // Wake up "write" operation on underlying "output" channel
        writeBackChannel.shutdown()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun writeBackCloseDoesNotThrowIfNotShutDown(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val output = object: AdbOutputChannel {
            var closeCalled = false

            override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                // This delay should be cancelled right away by `writeBackChannel.close`
                delay(1_000)
                return 0
            }

            override fun close() {
                closeCalled = true
            }
        }

        val writeBackChannel = channelFactory.createWriteBackChannel(output, 1_000)

        // Act
        val buffer = ByteBuffer.allocate(10)
        writeBackChannel.write(buffer)
        writeBackChannel.close()

        // Assert
        Assert.assertTrue(output.closeCalled)
    }

    @Test
    fun writeBackRethrowsCancellationException(): Unit = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val output = object: AdbOutputChannel {

            var callCount = 0

            var closed: Boolean = false
                private set

            var length: Int = 0
                private set

            override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
                callCount++
                if (callCount == 3) {
                    cancel("Fake I/O error")
                }
                val count = buffer.remaining()
                repeat(count) {
                    buffer.get()
                }
                length += count
                return length
            }

            override fun close() {
                closed = true
            }
        }

        val writeBackChannel = channelFactory.createWriteBackChannel(output, 1_000)

        // Act
        exceptionRule.expect(CancellationException::class.java)
        exceptionRule.expectMessage("Fake I/O error")
        repeat(10) {
            val buffer = ByteBuffer.allocate(100)
            writeBackChannel.write(buffer)
            delay(10)
        }
        writeBackChannel.shutdown()
        writeBackChannel.close()

        // Assert
        Assert.fail("Should not reach")
    }
}
