package dev.spinet.widget

import android.content.Context
import android.content.Intent
import dev.spinet.MainActivity

object WidgetIntents {
    const val ACTION_OPEN = "dev.spinet.widget.OPEN"

    fun activityIntent(context: Context, action: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
}
