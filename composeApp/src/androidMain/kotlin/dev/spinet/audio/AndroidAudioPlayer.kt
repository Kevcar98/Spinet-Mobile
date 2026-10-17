package dev.spinet.audio

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val appContext = context.applicationContext

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: Flow<PlaybackState> = _playbackState.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: Flow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: Flow<Long> = _durationMs.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlayingInfo?>(null)
    override val nowPlaying: Flow<NowPlayingInfo?> = _nowPlaying.asStateFlow()

    private val _transportCommands = MutableSharedFlow<TransportCommand>(extraBufferCapacity = 4)
    override val transportCommands: Flow<TransportCommand> = _transportCommands.asSharedFlow()

    private val _autoAdvanced = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val autoAdvanced: Flow<Unit> = _autoAdvanced.asSharedFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var controller: MediaController? = null

    private var pendingItem: MediaItem? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncState()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncNowPlaying()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncNowPlaying()

            controller?.let { c ->
                if (c.currentMediaItemIndex > 0) c.removeMediaItems(0, c.currentMediaItemIndex)
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) _autoAdvanced.tryEmit(Unit)
        }
        override fun onPlayerError(error: PlaybackException) {
            println("[AndroidAudioPlayer] playback error ${error.errorCodeName}: ${error.cause ?: error.message}")
            _playbackState.value = PlaybackState.ERROR
        }
    }

    init {
        MediaTransportBridge.onNext = { _transportCommands.tryEmit(TransportCommand.NEXT) }
        MediaTransportBridge.onPrevious = { _transportCommands.tryEmit(TransportCommand.PREVIOUS) }

        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                controller = future.get().also { it.addListener(listener) }
                val pending = pendingItem
                if (pending != null) {
                    pendingItem = null
                    startItem(pending)
                }
                syncState()

                syncNowPlaying()
            },
            ContextCompat.getMainExecutor(appContext)
        )

        scope.launch {
            while (true) {
                controller?.let { c ->
                    _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                    val d = c.duration
                    _durationMs.value = if (d > 0L) d else 0L
                }
                delay(500)
            }
        }
    }

    override fun play(url: String, title: String, artist: String, artworkUrl: String?) {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtwork(artworkUrl)
            .build()
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()

        val c = controller
        if (c == null) {
            pendingItem = item
            _playbackState.value = PlaybackState.BUFFERING
        } else {
            startItem(item)
        }
    }

    private fun startItem(item: MediaItem) {
        val c = controller ?: return

        c.setMediaItem(item)
        c.prepare()
        c.play()
    }

    override fun enqueueNext(url: String, title: String, artist: String, artworkUrl: String?) {
        val c = controller ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtwork(artworkUrl)
            .build()
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(metadata).build()

        dropAfterCurrent(c)
        c.addMediaItem(item)
    }

    override fun clearNext() {
        val c = controller ?: return
        dropAfterCurrent(c)
    }

    private fun dropAfterCurrent(c: MediaController) {
        val after = c.currentMediaItemIndex + 1
        if (c.mediaItemCount > after) c.removeMediaItems(after, c.mediaItemCount)
    }

    private fun syncNowPlaying() {
        val md = controller?.currentMediaItem?.mediaMetadata
        _nowPlaying.value = if (md?.title == null) {
            null
        } else {
            NowPlayingInfo(
                title = md.title?.toString().orEmpty(),
                artist = md.artist?.toString().orEmpty(),
                artworkUrl = md.artworkUri?.toString()
            )
        }
    }

    override fun pause() {
        controller?.pause()
    }

    override fun resume() {
        controller?.play()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    override fun release() {
        MediaTransportBridge.onNext = null
        MediaTransportBridge.onPrevious = null
        scope.cancel()
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    private fun syncState() {
        val c = controller ?: return
        _playbackState.value = when (c.playbackState) {
            Player.STATE_IDLE -> PlaybackState.IDLE
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> if (c.playWhenReady) PlaybackState.PLAYING else PlaybackState.PAUSED
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> PlaybackState.IDLE
        }
    }
}
