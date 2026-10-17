package dev.spinet.audio

object MediaTransportBridge {
    var onNext: (() -> Unit)? = null
    var onPrevious: (() -> Unit)? = null
}
