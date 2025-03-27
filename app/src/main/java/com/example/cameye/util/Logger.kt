package com.example.cameye.util

import timber.log.Timber

// Simple wrapper if you want to abstract Timber or add features later
object Logger {
    fun d(message: String, vararg args: Any?) = Timber.d(message, *args)
    fun i(message: String, vararg args: Any?) = Timber.i(message, *args)
    fun w(message: String, vararg args: Any?) = Timber.w(message, *args)
    fun e(t: Throwable? = null, message: String, vararg args: Any?) = Timber.e(t, message, *args)
    fun v(message: String, vararg args: Any?) = Timber.v(message, *args) // Verbose
}