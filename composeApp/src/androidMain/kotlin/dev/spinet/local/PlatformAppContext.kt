package dev.spinet.local

import android.content.Context

object PlatformAppContext {
    lateinit var app: Context

    val appOrNull: Context? get() = if (::app.isInitialized) app else null

    fun ensure(context: Context) {
        if (!::app.isInitialized) app = context.applicationContext
    }
}
