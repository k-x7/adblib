package com.android.adblib.impl.channels

import com.android.adblib.AdbOutputChannel
import com.android.adblib.AdbSessionHost
import com.android.adblib.thisLogger
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Implementation of [AdbOutputChannel] over a [OutputStream]
 */
internal class AdbOutputStreamChannel(
  private val host: AdbSessionHost,
  private val stream: OutputStream,
  bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
) : AdbOutputChannel {

    private val logger = thisLogger(host)

    private val bytes = ByteArray(bufferSize)

    override fun toString(): String {
        return "AdbOutputStreamChannel(\"$stream\")"
    }

    @Throws(Exception::class)
    override fun close() {
        logger.debug { "closing output stream channel" }
        stream.close()
    }

    override suspend fun write(buffer: ByteBuffer, timeout: Long, unit: TimeUnit): Int {
        return host.timeProvider.withErrorTimeout(timeout, unit) {
            // Note: Since OutputStream.write is a blocking I/O operation, we use the IO dispatcher
            runInterruptibleIO(host.blockingIoDispatcher) {
                val count = buffer.remaining()
                buffer.get(bytes, 0, count)
                stream.write(bytes, 0, count)
                count
            }
        }
    }
}
