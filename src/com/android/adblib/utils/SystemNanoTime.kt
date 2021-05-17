package com.android.adblib.utils

import com.android.adblib.SystemNanoTimeProvider

class SystemNanoTime : SystemNanoTimeProvider {
    override fun nanoTime(): Long {
        return System.nanoTime()
    }

    companion object {
        var instance = SystemNanoTime()
    }
}
