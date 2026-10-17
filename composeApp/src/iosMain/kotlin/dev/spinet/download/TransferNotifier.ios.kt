package dev.spinet.download

actual object TransferNotifier {
    actual fun start(title: String) {}
    actual fun update(fraction: Float, text: String) {}
    actual fun finish(text: String, ok: Boolean) {}
    actual fun cancel() {}
}
