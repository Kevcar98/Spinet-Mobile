package dev.spinet.local

import java.io.File

actual fun isLocalFileReadable(streamUrl: String): Boolean = when {
    streamUrl.isBlank() -> false

    streamUrl.startsWith("content://") -> true

    else -> runCatching { File(streamUrl.removePrefix("file://")).canRead() }
        .getOrDefault(false)
}
