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
package com.android.adblib.impl

import com.android.adblib.AdbChannel
import com.android.adblib.AdbDeviceServices
import com.android.adblib.utils.ResizableBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

/**
 * The packet headers are 5 bytes: 1 byte for the packet kind, and 4 bytes for the packet size.
 */
private const val SHELL_PACKET_HEADER_SIZE = 5

/**
 * Helper class to handle the shell v2 protocol
 *
 * See [ADB source code](https://cs.android.com/android/platform/superproject/+/master:packages/modules/adb/shell_protocol.h)
 * for inspiration for this class.
 *
 * @see [AdbDeviceServices.shellV2]
 */
internal class ShellV2ProtocolHandler(
    private val deviceChannel: AdbChannel,
    private val workBuffer: ResizableBuffer
) {

    init {
        // The "shell" protocol uses little endian order for serializing packet sizes
        workBuffer.order(ByteOrder.LITTLE_ENDIAN)
    }

    /**
     * Reads a shell packet and returns its length and payload as a [ByteBuffer] extracted
     * from [workBuffer]
     */
    suspend fun readPacket(timeout: TimeoutTracker): Pair<ShellV2PacketKind, ByteBuffer> {
        // Read header (1 byte for id, 4 bytes for data length)
        workBuffer.clear()
        deviceChannel.readExactly(
            workBuffer.forChannelRead(SHELL_PACKET_HEADER_SIZE),
            timeout.remainingNanos,
            TimeUnit.NANOSECONDS
        )
        val buffer = workBuffer.afterChannelRead()
        assert(buffer.remaining() == SHELL_PACKET_HEADER_SIZE)

        // Packet kind is first byte
        val packetKind = ShellV2PacketKind.fromValue(buffer.get().toInt())

        // Packet length is next 4 bytes (little endian)
        val packetLength = buffer.getInt()

        // Packet data is next "length" bytes
        workBuffer.clear()
        deviceChannel.readExactly(
            workBuffer.forChannelRead(packetLength),
            timeout.remainingNanos,
            TimeUnit.NANOSECONDS
        )
        return Pair(packetKind, workBuffer.afterChannelRead())
    }

    /**
     * Prepare the internal buffer for sending a packet, and returns a [ByteBuffer]
     * containing room for the data to be sent
     */
    fun prepareWriteBuffer(bufferSize: Int): ByteBuffer {
        workBuffer.clear()
        workBuffer.appendByte(0) // Kind will be set later
        workBuffer.appendInt(0) // Length will be set later
        assert(workBuffer.position == SHELL_PACKET_HEADER_SIZE)
        return workBuffer.forChannelRead(bufferSize - SHELL_PACKET_HEADER_SIZE)
    }

    /**
     * Write a packet of type [kind] using the data
     */
    suspend fun writePreparedBuffer(
        kind: ShellV2PacketKind,
        timeout: TimeoutTracker = TimeoutTracker.INFINITE
    ) {
        val buffer = workBuffer.afterChannelRead(0)
        // Buffer should contain header + data to send
        val packetLength = buffer.remaining() - SHELL_PACKET_HEADER_SIZE
        assert(packetLength >= 0)
        buffer.put(0, kind.value.toByte())
        buffer.putInt(1, packetLength)
        deviceChannel.writeExactly(buffer, timeout.remainingNanos, TimeUnit.NANOSECONDS)
    }
}

/**
 * Value of the "packet kind" byte in a shell v2 packet
 */
internal enum class ShellV2PacketKind(val value: Int) {

    STDIN(0),
    STDOUT(1),
    STDERR(2),
    EXIT_CODE(3),
    CLOSE_STDIN(4),
    WINDOW_SIZE_CHANGE(5),
    INVALID(255);

    companion object {

        fun fromValue(id: Int): ShellV2PacketKind {
            return values().firstOrNull { it.value == id } ?: INVALID
        }
    }
}
