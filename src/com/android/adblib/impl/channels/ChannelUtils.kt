/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.adblib.AdbSessionHost
import com.android.adblib.thisLogger
import com.android.adblib.utils.closeOnException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.InterruptedIOException
import java.nio.channels.AsynchronousSocketChannel
import java.nio.channels.Channel
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resumeWithException

/**
 * Suspends a coroutine around a [block] that operates on a [Channel] and takes a
 * [CancellableContinuation] to resume the coroutine execution.
 *
 * [block] is assumed to call [CancellableContinuation.resume] or
 * [CancellableContinuation.resumeWithException] when the underlying [channel] operation
 * completes.
 *
 * [channel] is automatically closed if any exception is thrown.
 *
 * Note: This method is `inline` to prevent object allocation(s) when capturing [block].
 */
internal suspend inline fun <T> suspendChannelCoroutine(
    host: AdbSessionHost,
    channel: Channel,
    crossinline block: (CancellableContinuation<T>) -> Unit
): T {
    return suspendCancellableCoroutine { continuation ->
        // Ensure channel is closed (and any pending async operation is cancelled) if
        // the coroutine is cancelled
        channel.closeOnCancel(host, continuation)

        wrapContinuationBlock(continuation) {
            block(continuation)
        }
    }
}

/**
 * Suspends a coroutine around a [block] that operates on a [Channel] and takes a
 * [CancellableContinuation] to resume the coroutine execution.
 *
 * [block] is assumed to call [CancellableContinuation.resume] or
 * [CancellableContinuation.resumeWithException] when the underlying [channel] operation
 * completes.
 *
 * [channel] is automatically closed if any exception is thrown.
 *
 * This method should be used when the underlying channel operation does not
 * natively support timeouts (e.g. [java.nio.channels.AsynchronousFileChannel]). The [timeout]
 * argument is used to implement a generic coroutine friendly timeout, using [withTimeout]
 * under the hood.
 *
 * Note: This method is `inline` to prevent object allocation(s) when capturing [block].
 */
internal suspend inline fun <T> suspendChannelCoroutine(
    host: AdbSessionHost,
    channel: Channel,
    timeout: Long,
    unit: TimeUnit,
    crossinline block: (CancellableContinuation<T>) -> Unit
): T {
    return if (timeout == Long.MAX_VALUE) {
        suspendChannelCoroutine(host, channel, block)
    } else {
        coroutineScope {
            // Run the channel coroutine in a child async scope, so that we can cancel
            // it with a timeout if needed.
            val deferredResult = async {
                suspendChannelCoroutine(host, channel, block)
            }
            host.timeProvider.withErrorTimeout(timeout, unit) {
                deferredResult.await()
            }
        }
    }
}

/**
 * Executes [block] on the [AdbSessionHost.ioDispatcher], setting up automatic cancellation if
 * [block] does not complete within the given [timeout].
 */
internal suspend inline fun <T> runBlockingWithTimeout(
    host: AdbSessionHost,
    closeable: Closeable,
    timeout: Long,
    unit: TimeUnit,
    crossinline block: () -> T
): T {
    return withContext(host.ioDispatcher) {
        // Run the channel coroutine in a child async scope, so that we can cancel
        // it with a timeout if needed.
        val deferredResult = async {
            block()
        }

        // Wait for completion until timeout, closes channel if timeout expires
        closeable.closeOnException {
            host.timeProvider.withErrorTimeout(timeout, unit) {
                deferredResult.await()
            }
        }
    }
}

private inline fun <T> wrapContinuationBlock(
    continuation: CancellableContinuation<T>,
    crossinline block: () -> Unit
) {
    // Note: Since `block` is *not* a `suspend`, so we need to handle exceptions and forward
    // them to the continuation to ensure to calling coroutine always completes.
    try {
        block()
    } catch (t: Throwable) {
        continuation.resumeWithException(t)
    }
}

/**
 * Ensures an [AsynchronousSocketChannel] is immediately closed when a coroutine is cancelled
 * (regular cancellation or exceptional cancellation) via its corresponding
 * [CancellableContinuation].
 *
 * Call this method to ensure that all pending operations on an asynchronous medium
 * (e.g. [AsynchronousSocketChannel]) are immediately terminated when a coroutine is cancelled
 *
 * [host] is used for logging purposes only.
 *
 * See [https://github.com/Kotlin/kotlinx.coroutines/blob/87eaba8a287285d4c47f84c91df7671fcb58271f/integration/kotlinx-coroutines-nio/src/Nio.kt#L126]
 * for the initial code this implementation is based on.
 */
private fun Closeable.closeOnCancel(host: AdbSessionHost, cont: CancellableContinuation<*>) {
    val logger = thisLogger(host)
    try {
        cont.invokeOnCancellation {
            logger.debug { "Closing resource because suspended coroutine has been cancelled" }
            try {
                close()
            } catch (t: Throwable) {
                // Some implementations of [Closeable] don't support asynchronous close in all cases, so we
                // have to ignore exceptions for those.
                logger.warn(t,"Error closing resource during cancellation, ignoring")
            }
        }
    } catch (t: Throwable) {
        // This can happen, for example, if invokeOnCancellation has already been called for
        // the cancellation
        logger.error(t, "Error registering cancellation handler for resource")
        throw t
    }
}


/**
 * Similar to [runInterruptible], but handles [InterruptedIOException] in addition to
 * [InterruptedException].
 */
internal suspend fun <T> runInterruptibleIO(
    context: CoroutineContext = EmptyCoroutineContext,
    block: () -> T
): T {
    return try {
        runInterruptible(context, block)
    } catch (e: InterruptedIOException) {
        throw CancellationException("Blocking call was interrupted due to parent cancellation").initCause(
            e
        )
    }
}
