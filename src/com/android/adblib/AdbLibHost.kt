package com.android.adblib

import com.android.adblib.utils.SystemNanoTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.nio.channels.AsynchronousChannelGroup

/**
 * The host of a single ADB instance. Calling the [.close] method on the host
 * should release all resources acquired for running the corresponding ADB instance.
 */
abstract class AdbLibHost : AutoCloseable {

    open val timeProvider: SystemNanoTimeProvider
        get() = SystemNanoTime()

    /**
     * The "main" or "root" logger from the [loggerFactory]
     */
    val logger: AdbLogger
        get() = loggerFactory.logger

    abstract val loggerFactory: AdbLoggerFactory

    /**
     * The [AsynchronousChannelGroup] used for running [java.nio.channels.AsynchronousSocketChannel] completions.
     *
     * The default value (`null`) corresponds to the default JVM value.
     */
    open val asynchronousChannelGroup: AsynchronousChannelGroup? = null

    /**
     * The coroutine dispatcher to use to execute I/O blocking operations.
     *
     * The default value is [Dispatchers.IO]
     */
    open val ioDispatcher = Dispatchers.IO

    /**
     * Release resources acquired by this host. Any operation still pending
     * will either be immediately cancelled or fail at time of completion.
     */
    @Throws(Exception::class)
    abstract override fun close()
}
