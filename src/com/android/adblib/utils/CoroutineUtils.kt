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
package com.android.adblib.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Same as [launch], except cancellation from the child coroutine [block] is propagated to the
 * parent coroutine (scope).
 *
 * The behavior is the same as [launch] wrt the following aspects:
 * * The parent coroutine won't complete until the child coroutine [block] completes.
 * * The parent coroutine fails with an exception if the child coroutine [block] throws
 *   an exception.
 */
internal inline fun CoroutineScope.launchCancellable(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    crossinline block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(context, start) {
        try {
            block()
        } catch (e: CancellationException) {
            // Note: this is a no-op is the parent scope is already cancelled
            this@launchCancellable.cancel(e)
            throw e
        }
    }
}

/**
 * Calls [block] while collecting the first element of this [Flow], leaving the [Flow]
 * active while [block] executes.
 *
 * Note: This is different from [Flow.first], which terminates the flow as soon as
 * the first flow element has been collected and then returns the first value.
 */
suspend fun <T, R> Flow<T>.firstCollecting(
    parentScope: CoroutineScope,
    block: suspend (T) -> R
): R {
    return firstCollecting(parentScope).use {
        block(it.value())
    }
}

/**
 * Return a [FirstCollecting] while collecting the first element of this [Flow], leaving the [Flow]
 * active until [FirstCollecting.close] is called. The first element of the [Flow] is available
 * by calling [FirstCollecting.value].
 *
 * Note: This is different from [Flow.first], which terminates the flow as soon as
 * the first flow element has been collected and then returns the first value.
 */
fun <T> Flow<T>.firstCollecting(parentScope: CoroutineScope): FirstCollecting<T> {
    return FirstCollectingImpl(parentScope, this)
}

interface FirstCollecting<out T> : AutoCloseable {

    suspend fun value(): T
}

internal class FirstCollectingImpl<T>(
    parentScope: CoroutineScope,
    private val flow: Flow<T>
) : FirstCollecting<T>, AutoCloseable {

    private val flowScope = CoroutineScope(parentScope.coroutineContext + Job())

    private val lazyValue = SuspendingLazy { startCollect() }

    @Volatile
    private var item: T? = null

    private suspend fun startCollect(): T {
        val channel = Channel<T>()
        flowScope.launch {
            try {
                flow.collect {
                    channel.send(it)

                    // By returning here, we let the flow continue to run in this coroutine.
                    // A well-behaved flow will eventually end with no additional items.
                }
            } catch (t: Throwable) {
                channel.close(t)
                throw t
            }
        }
        return channel.receive().also {
            item = it
        }
    }

    override suspend fun value(): T {
        return lazyValue.value()
    }

    override fun close() {
        flowScope.cancel(CancellationException("FirstCollecting() has been closed"))
        (item as? AutoCloseable)?.close()
    }
}
