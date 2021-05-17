package com.android.adblib

interface SystemNanoTimeProvider {
    /**
     * Returns the number of nano seconds since the system was started.
     * See [System.nanoTime]
     */
    fun nanoTime(): Long
}