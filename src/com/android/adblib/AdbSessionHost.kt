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

import com.android.adblib.utils.JdkLoggerFactory
import com.android.adblib.utils.SystemNanoTime
import kotlinx.coroutines.Dispatchers
import java.nio.channels.AsynchronousChannelGroup
import javax.swing.SwingUtilities

/**
 * The host of a single ADB instance. Calling the [.close] method on the host
 * should release all resources acquired for running the corresponding ADB instance.
 */
open class AdbSessionHost : AutoCloseable {

    open val timeProvider: SystemNanoTimeProvider
        get() = SystemNanoTime()

    /**
     * The "main" or "root" logger from the [loggerFactory]
     */
    val logger: AdbLogger
        get() = loggerFactory.logger

    open val loggerFactory: AdbLoggerFactory = JdkLoggerFactory()

    /**
     * The [AsynchronousChannelGroup] used for running [java.nio.channels.AsynchronousSocketChannel] completions.
     *
     * The default value (`null`) corresponds to the default JVM value.
     */
    open val asynchronousChannelGroup: AsynchronousChannelGroup? = null

    /**
     * The coroutine dispatcher to use to execute asynchronous I/O and
     * compute intensive operations.
     *
     * The default value is [Dispatchers.Default]
     */
    open val ioDispatcher
        get() = Dispatchers.Default

    /**
     * The coroutine dispatcher to use to execute blocking I/O blocking operations.
     *
     * The default value is [Dispatchers.IO]
     */
    open val blockingIoDispatcher
        get() = Dispatchers.IO

    /**
     * Returns `true` if the current thread runs an event dispatching queue that should **not**
     * allow blocking operations, e.g. the current thread is an AWT event dispatching thread.
     *
     * @see SwingUtilities.isEventDispatchThread()
     */
    open val isEventDispatchThread: Boolean
        get() = SwingUtilities.isEventDispatchThread()

    /**
     * Release resources acquired by this host. Any operation still pending
     * will either be immediately cancelled or fail at time of completion.
     */
    @Throws(Exception::class)
    override fun close() {
        // Nothing to do by default
    }
}
