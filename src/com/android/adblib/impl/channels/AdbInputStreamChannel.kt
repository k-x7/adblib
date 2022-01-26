package com.android.adblib.impl.channels

import com.android.adblib.AdbInputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.thisLogger
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlin.math.min

const val DEFAULT_CHANNEL_BUFFER_SIZE = DEFAULT_BUFFER_SIZE

/**
 * Implementation of [AdbInputChannel] over an arbitrary [InputStream]
 */
internal class AdbInputStreamChannel(
  private val host: AdbSessionHost,
  private val stream: InputStream,
  bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
) : AdbInputChannel {

    private val logger = thisLogger(host)

    private val bytes = ByteArray(bufferSize)

    @Throws(Exception::class)
    override fun close() {
        logger.debug { "Closing" }
        stream.close()
    }

    override suspend fun read(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return host.timeProvider.withErrorTimeout(timeout, unit) {
            // Note: Since InputStream.read is a blocking I/O operation, we use the IO dispatcher
            runInterruptibleIO(host.blockingIoDispatcher) {
                val count = stream.read(bytes, 0, min(bytes.size, buffer.remaining()))
                logger.debug { "Read $count bytes from input stream" }
                if (count > 0) {
                    buffer.put(bytes, 0, count)
                }
                count
            }
        }
    }
}
