package dev.spinet.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrlExternally(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}
