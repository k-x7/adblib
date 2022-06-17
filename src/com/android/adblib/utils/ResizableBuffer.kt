package com.android.adblib.utils

import com.android.adblib.AdbChannel
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.InvalidMarkException
import java.nio.charset.Charset

/**
 * A [ResizableBuffer] is a [ByteBuffer] wrapper that allows dynamic resizing of the buffer
 * when additional room is needed and supports switching between `reading and `writing` mode.
 *
 * * Reading data from an external source and then using that data is done through
 *   calling [forChannelRead] and [afterChannelRead] methods.
 *
 * * Writing data to an external source is done through preparing the buffer with data
 *   (e.g. with the [appendBytes] method) and then writing using the [forChannelWrite] method.
 *
 * Switching between the `reading` and `writing` mode is done by calling the [clear] method.
 */
class ResizableBuffer(initialCapacity: Int = 256, private val maxCapacity: Int = Int.MAX_VALUE) {

    private var buffer: ByteBuffer = ByteBuffer.allocate(initialCapacity)

    /**
     * Returns the current reading or writing position (e.g. for [appendInt]).
     */
    val position: Int
        get() = buffer.position()

    /**
     * Returns the current size (in bytes) of allocated memory for this [ResizableBuffer].
     */
    val capacity: Int
        get() = buffer.capacity()

    /**
     * Absolute get method. Reads the byte at the given [index], which can range from
     * 0 to [ByteBuffer.limit].
     */
    operator fun get(index: Int): Byte {
        return buffer[index]
    }

    /**
     * Clears this buffer (see [ByteBuffer.clear]), resetting the position to `0`, and
     * the [ByteBuffer.limit] to [ByteBuffer.capacity].
     * This is typically used before appending data or before calling for `channel read`
     * operations (see [forChannelRead]).
     *
     * * If the buffer is needed for writing data to a channel, call the various
     *   [appendBytes] methods to add data then call [forChannelWrite].
     *
     * * If the buffer is needed for reading data from a channel, call [forChannelRead] immediately
     *   after this method, call the `channel read` operation, then call [afterChannelRead] when
     *   the `channel read` operation is completed.
     */
    fun clear() {
        buffer.clear()
    }

    /**
     * Clears this buffer (see [ByteBuffer.clear]), resetting the position to [position], and
     * the [ByteBuffer.limit] to [ByteBuffer.capacity].
     * This is typically used before appending data or before calling for `channel read`
     * operations (see [forChannelRead]).
     *
     * * If the buffer is needed for writing data to a channel, call the various
     *   [appendBytes] methods to add data then call [forChannelWrite].
     *
     * * If the buffer is needed for reading data from a channel, call [forChannelRead] immediately
     *   after this method, call the `channel read` operation, then call [afterChannelRead] when
     *   the `channel read` operation is completed.
     */
    fun clearToPosition(newPosition: Int) {
        buffer.clear()
        buffer.position(newPosition)
    }

    /**
     * This method returns the internal [ByteBuffer] after ensuring it is ready for using in
     * some sort of `channel write` operation (e.g. [AdbChannel.write]).
     *
     * This method is typically called after adding data using the various `appendXxx` methods
     * (e.g. [appendBytes]) to this buffer.
     *
     * Once the `channel write` operation is finished, the caller is responsible for calling the
     * [clear] method so the buffer can be used for writing again.
     *
     * Note: The returned [ByteBuffer] instance may become invalid if any other [ResizableBuffer]
     * operation is invoked.
     */
    fun forChannelWrite(): ByteBuffer {
        if (buffer.limit() != buffer.capacity()) {
            throw IllegalStateException("Buffer has not been reset and can't be used for a write operation")
        }

        // Data is from 0 to position, so limit = position, position = 0
        buffer.flip()
        return buffer
    }

    /**
     * Returns the underlying [ByteBuffer] after [marking][ByteBuffer.mark] the current position
     * and ensuring that the buffer has exactly [length] bytes available between
     * [buffer.position()] and [buffer.limit()], typically before a `read` operation that
     * requires a [ByteBuffer].
     *
     * Once the `read` operation is done, call [afterChannelRead] to reset the buffer position
     * to the position marked by this method, so that the buffer data from [buffer.position()]
     * to [buffer.limit()] can be analyzed
     *
     * Note: The returned [ByteBuffer] instance may become invalid if any other [ResizableBuffer]
     * operation is invoked.
     */
    fun forChannelRead(length: Int): ByteBuffer {
        if (length < 0) {
            throw IllegalArgumentException("Length should be greater or equal to 0")
        }

        if (buffer.limit() != buffer.capacity()) {
            throw IllegalStateException("Buffer has not been reset and can't be used for a read operation")
        }

        ensureRoom(length)
        buffer.limit(buffer.position() + length)
        buffer.mark() // so that position can be restored when calling 'afterReading'
        assert(buffer.remaining() == length)
        return buffer
    }

    /**
     * Returns the underlying [ByteBuffer] after a `channel read` operation so that data can
     * be read from the [ByteBuffer].
     */
    fun afterChannelRead(newPosition: Int = -1): ByteBuffer {
        try {
            // Data is from `mark` to `position`, so set limit = position, position = mark, and mark = -1
            val newLimit = buffer.position()
            buffer.reset() // reset position to mark
            val mark = if (newPosition >= 0) newPosition else buffer.position()
            buffer.rewind() // Clear mark (i.e. -1)
            buffer.position(mark)
            buffer.limit(newLimit)
        } catch (e: InvalidMarkException) {
            throw IllegalStateException("Buffer has not been prepared for a read operation", e)
        }
        return buffer
    }

    /**
     * Transfers the entire content of the given source byte array into this buffer at
     * the current buffer position, after resizing the buffer if it is full.
     */
    fun appendBytes(src: ByteArray) {
        ensureRoom(src.size)
        buffer.put(src)
    }

    /**
     * Transfers the entire content of the given source [ByteBuffer] into this buffer at
     * the current buffer position, after resizing the buffer if it is full.
     */
    fun appendBytes(src: ByteBuffer) {
        ensureRoom(src.remaining())
        buffer.put(src)
    }

    /**
     * Transfers the entire content of the given source string into this buffer at
     * the current buffer position, after resizing the buffer if it is full.
     */
    fun appendString(value: String, charset: Charset) {
        appendBytes(charset.encode(value))
    }

    fun appendByte(value: Byte) {
        ensureRoom(Byte.SIZE_BYTES)
        this.buffer.put(value)
    }

    fun appendInt(value: Int) {
        ensureRoom(Int.SIZE_BYTES)
        buffer.putInt(value)
    }

    fun setInt(index: Int, value: Int) {
        buffer.putInt(index, value)
    }

    /**
     * Ensures the buffer has at least [length] available bytes between [buffer.position()]
     * and [buffer.limit()], allocating a new internal buffer if necessary
     */
    private fun ensureRoom(length: Int) {
        buffer = growBuffer(buffer, length, maxCapacity)
    }

    fun order(bo: ByteOrder): ResizableBuffer {
        buffer.order(bo)
        return this
    }

    companion object {

        /**
         * Returns a copy of [buffer] that has enough room for at least [length] more bytes
         * after [ByteBuffer.position]
         */
        fun growBuffer(buffer: ByteBuffer, length: Int, maxCapacity: Int): ByteBuffer {
            assert(buffer.limit() == buffer.capacity())

            if (maxCapacity < 0) {
                throw IllegalArgumentException("Maximum capacity must be a positive value")
            }
            if (length < 0) {
                throw IllegalArgumentException("Length must be a positive value")
            }

            if (buffer.remaining() >= length) {
                return buffer
            }

            if (buffer.capacity() >= maxCapacity) {
                throw IllegalArgumentException("Buffer cannot grow as it has reached maximum capacity")
            }
            if (buffer.position() + length > maxCapacity) {
                throw IllegalArgumentException("Buffer cannot grow to additional requested capacity")
            }
            val newCapacity = nextCapacity(buffer, buffer.position() + length, maxCapacity)
            val newBuffer = ByteBuffer.allocate(newCapacity)
            newBuffer.order(buffer.order())
            buffer.flip()
            // Copy the previous buffer, set position and limit to the same as the previous buffer
            newBuffer.put(buffer)

            assert(newBuffer.limit() == newBuffer.capacity())
            assert(newBuffer.remaining() >= length)
            return newBuffer
        }

        private fun nextCapacity(
            byteBuffer: ByteBuffer,
            atLeastCapacity: Int,
            maxCapacity: Int
        ): Int {
            var capacity = byteBuffer.capacity().coerceAtLeast(1)
            while (capacity < atLeastCapacity) {
                capacity *= 2
            }
            return capacity.coerceAtMost(maxCapacity)
        }
    }
}

fun <T> ByteBuffer.withOrder(bo: ByteOrder, block: () -> T): T {
    val saved = this.order()
    this.order(bo)
    val result = block()
    this.order(saved)
    return result
}
