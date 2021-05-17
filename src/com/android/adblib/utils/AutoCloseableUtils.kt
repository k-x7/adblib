package com.android.adblib.utils

/**
 * Ensure [AutoCloseable.close] is called if [block] throws an exception. This method can be used
 * to prevent leaking [AutoCloseable] during initialization, i.e. before they are visible to
 * callers.
 */
inline fun <T : AutoCloseable, R> T.closeOnException(block: (T) -> R): R {
    try {
        return block(this)
    } catch (e: Throwable) {
        this.safeClose(e)
        throw e
    }
}

fun AutoCloseable.safeClose(cause: Throwable) {
    try {
        close()
    } catch (closeException: Throwable) {
        cause.addSuppressed(closeException)
    }
}
