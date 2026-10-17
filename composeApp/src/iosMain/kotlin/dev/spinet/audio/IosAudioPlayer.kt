package dev.spinet.audio

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class IosAudioPlayer : AudioPlayer {
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: Flow<PlaybackState> = _playbackState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: Flow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: Flow<Long> = _durationMs.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlayingInfo?>(null)
    override val nowPlaying: Flow<NowPlayingInfo?> = _nowPlaying.asStateFlow()

    private val _transportCommands = MutableSharedFlow<TransportCommand>()
    override val transportCommands: Flow<TransportCommand> = _transportCommands.asSharedFlow()

    override fun play(url: String, title: String, artist: String, artworkUrl: String?) {
    }

    override fun pause() {
    }

    override fun resume() {
    }

    override fun seekTo(positionMs: Long) {
    }

    override fun release() {
    }
}
