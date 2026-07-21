package com.drishti.utils

import android.util.Log

object Logger {

    enum class Level {
        DEBUG, INFO, WARNING, ERROR
    }

    private var isDebugEnabled = true

    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    fun isDebugEnabled(): Boolean = isDebugEnabled

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        if (isDebugEnabled) {
            Log.d(tag, message, throwable)
        }
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}
