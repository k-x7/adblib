package com.android.adblib.impl.channels

import com.android.adblib.AdbLibHost
import com.android.adblib.AdbOutputChannel
import com.android.adblib.thisLogger
import com.android.adblib.utils.TimeoutTracker
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Implementation of [AdbOutputChannel] over a [OutputStream]
 */
internal class AdbOutputStreamChannel(
    private val host: AdbLibHost,
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

    override suspend fun write(buffer: ByteBuffer, timeout: TimeoutTracker): Int {
        return withContext(host.ioDispatcher) {
            val deferredCount = async {
                val count = buffer.remaining()
                buffer.get(bytes, 0, count)
                @Suppress("BlockingMethodInNonBlockingContext")
                stream.write(bytes, 0, count)
                count
            }
            host.timeProvider.withErrorTimeout(timeout.remainingMills) {
                deferredCount.await()
            }
        }
    }
}
