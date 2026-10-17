package dev.spinet.local

import platform.Foundation.NSFileManager

actual fun isLocalFileReadable(streamUrl: String): Boolean {
    if (streamUrl.isBlank()) return false
    return NSFileManager.defaultManager.isReadableFileAtPath(streamUrl.removePrefix("file://"))
}
