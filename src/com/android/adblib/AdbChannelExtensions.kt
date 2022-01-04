package com.android.adblib

import com.android.adblib.impl.channels.AdbInputStreamChannel
import com.android.adblib.impl.channels.DEFAULT_CHANNEL_BUFFER_SIZE
import com.android.adblib.utils.AdbProtocolUtils
import com.android.adblib.utils.TimeoutTracker
import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Forwards the contents of this [AdbInputChannel] to an [AdbOutputChannel]
 */
suspend fun AdbInputChannel.forwardTo(
    session: AdbLibSession,
    outputChannel: AdbOutputChannel,
    bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
) {
    val host = session.host
    host.logger.info { "forwardChannel - Forwarding input channel to output channel using buffer of size $bufferSize bytes" }
    val buffer = ByteBuffer.allocate(bufferSize)
    while (true) {
        buffer.clear()
        val byteCount = read(buffer, TimeoutTracker.INFINITE)
        if (byteCount < 0) {
            // EOF, nothing left to forward
            host.logger.info { "forwardChannel - end of input channel reached, done" }
            break
        }

        host.logger.debug { "forwardChannel - forwarding packet of $byteCount bytes" }
        buffer.flip()
        outputChannel.writeExactly(buffer, TimeoutTracker.INFINITE)
    }
}

fun InputStream.asAdbInputChannel(
    session: AdbLibSession,
    bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
): AdbInputChannel {
    return AdbInputStreamChannel(session.host, this, bufferSize)
}

fun String.asAdbInputChannel(
    session: AdbLibSession,
    bufferSize: Int = DEFAULT_CHANNEL_BUFFER_SIZE
): AdbInputChannel {
    //TODO: This is inefficient as `byteInputStream` creates an in-memory copy of the whole string
    return byteInputStream(AdbProtocolUtils.ADB_CHARSET).asAdbInputChannel(session, bufferSize)
}
