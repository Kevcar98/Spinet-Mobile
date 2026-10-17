package dev.spinet.download

expect object TransferNotifier {
    fun start(title: String)

    fun update(fraction: Float, text: String)

    fun finish(text: String, ok: Boolean)

    fun cancel()
}
