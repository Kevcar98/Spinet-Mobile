package dev.spinet.util

import android.content.Intent
import android.net.Uri
import dev.spinet.local.PlatformAppContext

actual fun openUrlExternally(url: String) {
    val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}
