package dev.spinet.download

import kotlin.concurrent.Volatile

object TransferController {
    @Volatile
    var cancelled: Boolean = false
        private set

    @Volatile
    var onCancel: (() -> Unit)? = null

    fun reset() {
        cancelled = false
    }

    fun requestCancel() {
        cancelled = true
        onCancel?.invoke()
    }
}
