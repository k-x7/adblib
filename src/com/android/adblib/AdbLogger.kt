package com.android.adblib

abstract class AdbLogger {

    abstract fun log(level: Level, message: String)
    abstract fun log(level: Level, exception: Throwable, message: String)
    abstract val minLevel: Level

    fun verbose(message: String) {
        log(Level.VERBOSE, message)
    }

    fun verbose(exception: Throwable, message: String) {
        log(Level.VERBOSE, exception, message)
    }

    fun debug(message: String) {
        log(Level.DEBUG, message)
    }

    fun debug(exception: Throwable, message: String) {
        log(Level.DEBUG, exception, message)
    }

    fun info(message: String) {
        log(Level.INFO, message)
    }

    fun info(exception: Throwable, message: String) {
        log(Level.INFO, exception, message)
    }

    fun warn(message: String) {
        log(Level.WARN, message)
    }

    fun warn(exception: Throwable, message: String) {
        log(Level.WARN, exception, message)
    }

    fun error(message: String) {
        log(Level.ERROR, message)
    }

    fun error(exception: Throwable, message: String) {
        log(Level.ERROR, exception, message)
    }

    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}
