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
import com.android.adblib.testingutils.CoroutineTestUtils.yieldUntil
import com.android.adblib.testingutils.TestingAdbSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class AdbPipedInputChannelTest {

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
    fun testReadingWritingAndClosingWorks() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val pipedChannel = channelFactory.createPipedChannel(15)

        // Act
        val deferredCount = async {
            var total = 0
            val buffer = ByteBuffer.allocate(10)
            while(true) {
                buffer.clear()
                val count = pipedChannel.read(buffer)
                if (count < 0) {
                    break
                }
                total += count
            }
            total
        }

        pipedChannel.pipeSource.writeNBytes(count = 100, times = 15)
        pipedChannel.pipeSource.close() // Sends EOF
        val readCount = deferredCount.await()

        // Assert
        Assert.assertEquals(1_500, readCount)
    }

    @Test
    fun testCancellationWorks() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val pipedChannel1 = channelFactory.createPipedChannel(15)
        val pipedChannel2 = channelFactory.createPipedChannel(15)

        // Act
        var readStarted1 = false
        var readStarted2 = false
        val job = async {
            launch {
                readStarted1 = true
                // Blocked until cancellation
                pipedChannel1.read(ByteBuffer.allocate(10))
            }
            launch {
                readStarted2 = true
                // Blocked until cancellation
                pipedChannel2.read(ByteBuffer.allocate(10))
            }
        }

        yieldUntil { readStarted1 && readStarted2 }
        job.cancel(CancellationException("My Cancellation"))

        exceptionRule.expect(CancellationException::class.java)
        @Suppress("DeferredResultUnused")
        job.await()

        // Assert
        Assert.fail("Should not reach")
    }

    @Test
    fun testReadTimeoutWorks() = runBlockingWithTimeout {
        // Prepare
        val session = registerCloseable(TestingAdbSession())
        val channelFactory = AdbChannelFactoryImpl(session)
        val pipedChannel = channelFactory.createPipedChannel(15)

        // Act
        exceptionRule.expect(TimeoutCancellationException::class.java)
        pipedChannel.read(ByteBuffer.allocate(10), 10, TimeUnit.MILLISECONDS)

        // Assert
        Assert.fail("Should not reach")
    }

    private suspend fun AdbOutputChannel.writeNBytes(count: Int, times: Int) {
        val buffer = ByteBuffer.allocate(count)
        repeat(times) {
            buffer.clear()
            repeat(count) {
                buffer.put((count % 255).toByte())
            }
            buffer.flip()
            writeExactly(buffer)
        }
    }
}
