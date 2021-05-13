package com.android.adblib

abstract class AdbLogger {

    abstract fun log(level: Level, message: String)
    abstract fun log(level: Level, exception: Throwable, message: String)

    fun log(level: Level, format: String, vararg args: Any?) {
        log(level, String.format(format, *args))
    }

    fun log(level: Level, exception: Throwable, format: String, vararg args: Any?) {
        log(level, exception, String.format(format, *args))
    }

    fun verbose(message: String) {
        log(Level.VERBOSE, message)
    }

    fun verbose(format: String, vararg args: Any?) {
        log(Level.VERBOSE, format, *args)
    }

    fun verbose(exception: Throwable, message: String) {
        log(Level.VERBOSE, exception, message)
    }

    fun verbose(exception: Throwable, format: String, vararg args: Any?) {
        log(Level.VERBOSE, exception, format, *args)
    }

    fun debug(message: String) {
        log(Level.DEBUG, message)
    }

    fun debug(format: String, vararg args: Any?) {
        log(Level.DEBUG, format, *args)
    }

    fun debug(exception: Throwable, message: String) {
        log(Level.DEBUG, exception, message)
    }

    fun debug(exception: Throwable, format: String, vararg args: Any?) {
        log(Level.DEBUG, exception, format, *args)
    }

    fun info(message: String) {
        log(Level.INFO, message)
    }

    fun info(format: String, vararg args: Any?) {
        log(Level.INFO, format, *args)
    }

    fun info(exception: Throwable, message: String) {
        log(Level.INFO, exception, message)
    }

    fun info(exception: Throwable, format: String, vararg args: Any?) {
        log(Level.INFO, exception, format, *args)
    }

    fun warn(message: String) {
        log(Level.WARN, message)
    }

    fun warn(format: String, vararg args: Any?) {
        log(Level.WARN, format, *args)
    }

    fun warn(exception: Throwable, message: String) {
        log(Level.WARN, exception, message)
    }

    fun warn(exception: Throwable, format: String, vararg args: Any?) {
        log(Level.WARN, exception, format, *args)
    }

    fun error(message: String) {
        log(Level.ERROR, message)
    }

    fun error(format: String, vararg args: Any?) {
        log(Level.ERROR, format, *args)
    }

    fun error(exception: Throwable, message: String) {
        log(Level.ERROR, exception, message)
    }

    fun error(exception: Throwable, format: String, vararg args: Any?) {
        log(Level.ERROR, exception, format, *args)
    }

    enum class Level {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
}
