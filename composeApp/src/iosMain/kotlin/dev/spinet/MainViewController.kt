package dev.spinet

import androidx.compose.ui.window.ComposeUIViewController
import dev.spinet.audio.IosAudioPlayer
import dev.spinet.download.IosFileDownloader

@Suppress("FunctionName", "unused")
fun MainViewController() = ComposeUIViewController {
    App(
        audioPlayer = IosAudioPlayer(),
        fileDownloader = IosFileDownloader()
    )
}
