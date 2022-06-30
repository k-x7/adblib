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

import com.android.adblib.impl.channels.AdbChannelFactoryImpl
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.TestingAdbSessionHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.TimeUnit

class AdbChannelFactoryTest {

    @JvmField
    @Rule
    val closeables = CloseablesRule()

    @JvmField
    @Rule
    var exceptionRule: ExpectedException = ExpectedException.none()

    private fun <T : AutoCloseable> registerCloseable(item: T): T {
        return closeables.register(item)
    }

    @Test
    fun testConnectSocketWorks() = runBlockingWithTimeout {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        val serverAddress = serverSocket.bind()

        // Act
        launch {
            serverSocket.accept().use { socket ->
                socket.readString()
                socket.writeString("World")
                socket.shutdownOutput()
            }
        }

        val serverMessage = channelFactory.connectSocket(serverAddress).use { clientSocket ->
            clientSocket.writeString("Hello")
            clientSocket.shutdownOutput()
            clientSocket.readString()
        }

        // Assert
        assertEquals("World", serverMessage)
    }

    //@Test // Disabled for now, because server socket backlog behavior is platform dependent
    fun testConnectSocketWithTimeoutWorks() = runBlocking {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        val serverAddress = serverSocket.bind(backLog = 1)

        // Act: We have a backlog of "1", so first connect succeeds, second one should fail
        registerCloseable(channelFactory.connectSocket(serverAddress))
        exceptionRule.expect(TimeoutCancellationException::class.java)
        channelFactory.connectSocket(serverAddress, 500, TimeUnit.MILLISECONDS)

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testConnectSocketReadIsClosedWhenPendingReadReachesTimeout() = runBlocking {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        val serverAddress = serverSocket.bind()

        // Act
        launch {
            serverSocket.accept().use {
                // Don't send anything, so that client read waits for at least 1 second
                delay(1_000)
            }
        }
        channelFactory.connectSocket(serverAddress).use { clientSocket ->
            val buffer = ByteBuffer.allocate(10)
            try {
                clientSocket.read(buffer, 100, TimeUnit.MILLISECONDS)
            } catch (_: IOException) {
            }

            exceptionRule.expect(Exception::class.java)
            clientSocket.read(buffer, 100, TimeUnit.MILLISECONDS)
        }

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testConnectSocketReadIsClosedWhenPendingReadIsCancelled() = runBlocking {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        val serverAddress = serverSocket.bind()

        // Act
        launch {
            serverSocket.accept().use {
                // Don't send anything, so that client read waits for at least 1 second
                delay(1_000)
            }
        }
        channelFactory.connectSocket(serverAddress).use { clientSocket ->
            val buffer = ByteBuffer.allocate(10)
            withTimeoutOrNull(100) {
                clientSocket.read(buffer)
            }

            exceptionRule.expect(Exception::class.java)
            clientSocket.read(buffer, 100, TimeUnit.MILLISECONDS)
        }

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testConnectSocketReadTimeoutWorks() = runBlocking {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        val serverAddress = serverSocket.bind()

        // Act
        launch {
            serverSocket.accept().use {
                // Don't send anything, so that client read waits for at least 1 second
                delay(1_000)
            }
        }
        channelFactory.connectSocket(serverAddress).use { clientSocket ->
            val buffer = ByteBuffer.allocate(10)
            exceptionRule.expect(IOException::class.java)
            clientSocket.read(buffer, 100, TimeUnit.MILLISECONDS)
        }

        // Assert
        fail("Should not reach")
    }

    @Test
    fun testServerSocketHasNullLocalAddressByDefault() = runBlockingWithTimeout {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)

        // Act
        val serverSocket = registerCloseable(channelFactory.createServerSocket())

        // Assert
        assertNull(serverSocket.localAddress())
    }


    @Test
    fun testServerSocketBindWorks() = runBlockingWithTimeout {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())

        // Act
        val localAddress = serverSocket.bind()

        // Assert
        assertNotNull(serverSocket.localAddress())
        assertEquals(localAddress, serverSocket.localAddress())
    }

    @Test
    fun testServerSocketAcceptWorks() = runBlockingWithTimeout {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        val serverAddress = serverSocket.bind()

        // Act
        val job1 = async(Dispatchers.IO) {
            serverSocket.accept().use { socket ->
                val message = socket.readString()
                socket.writeString("World")
                socket.shutdownOutput()
                message
            }
        }
        val job2 = async(Dispatchers.IO) {
            channelFactory.connectSocket(serverAddress).use { clientSocket ->
                clientSocket.writeString("Hello")
                clientSocket.shutdownOutput()
                clientSocket.readString()
            }
        }

        // Assert
        assertEquals("Hello", job1.await())
        assertEquals("World", job2.await())
    }

    @Test
    fun testServerSocketCancellationOfPendingAcceptClosesSocket() = runBlockingWithTimeout {
        // Prepare
        val host = registerCloseable(TestingAdbSessionHost())
        val channelFactory = AdbChannelFactoryImpl(host)
        val serverSocket = registerCloseable(channelFactory.createServerSocket())
        serverSocket.bind()

        // Act
        val job1 = launch {
            serverSocket.accept().use {
                throw Exception("Accept should not complete")
            }
        }
        delay(200)
        job1.cancelAndJoin()

        exceptionRule.expect(ClosedChannelException::class.java)
        serverSocket.accept()

        // Assert
        fail("Should not reach")
    }

    private suspend fun AdbChannel.writeInt(value: Int) {
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(value)
        buffer.flip()
        writeExactly(buffer)
    }

    private suspend fun AdbChannel.readInt(): Int {
        val buffer = ByteBuffer.allocate(4)
        readExactly(buffer)
        buffer.flip()
        return buffer.int
    }

    private suspend fun AdbChannel.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)

        val buffer = ByteBuffer.wrap(bytes)
        writeExactly(buffer)
    }

    private suspend fun AdbChannel.readString(): String {
        val length = readInt()

        val buffer = ByteBuffer.allocate(length)
        readExactly(buffer)

        return String(buffer.array(), Charsets.UTF_8)
    }
}
