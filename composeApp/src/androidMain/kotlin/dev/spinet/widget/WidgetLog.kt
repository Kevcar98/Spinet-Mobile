package dev.spinet.widget

import android.util.Log

internal object WidgetLog {
    const val TAG = "SpinetWidget"

    fun d(message: String) {
        Log.d(TAG, message)
    }

    fun w(message: String, error: Throwable? = null) {
        if (error != null) Log.w(TAG, message, error) else Log.w(TAG, message)
    }

    fun e(message: String, error: Throwable? = null) {
        if (error != null) Log.e(TAG, message, error) else Log.e(TAG, message)
    }

    inline fun <T> guard(label: String, block: () -> T): T? = try {
        block()
    } catch (e: Throwable) {
        e("$label failed", e)
        null
    }
}
