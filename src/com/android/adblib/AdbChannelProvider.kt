package com.android.adblib

import com.android.adblib.utils.TimeoutTracker

/**
 * An provider of [AdbChannel] instances ready for communication with an ADB host
 *
 * This is used to ensure various service implementations don't depend on concrete implementations
 * of acquiring connections to the ADB host.
 */
interface AdbChannelProvider {
    suspend fun createChannel(timeout: TimeoutTracker): AdbChannel
}
