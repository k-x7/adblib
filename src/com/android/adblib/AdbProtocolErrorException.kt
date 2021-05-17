package com.android.adblib

import java.io.IOException

/**
 * Exception thrown when an invalid/unexpected reply came from the ADB server.
 * This should result in closing the underlying the (socket) connection, since
 * there is no reliable way to "re-sync" the connection to a known state.
 */
class AdbProtocolErrorException : IOException {
    constructor(message: String) : super(message) {}
    constructor(message: String, cause: Throwable) : super(message, cause) {}
}
