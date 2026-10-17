package dev.spinet.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.spinet.data.nowEpochMillis
import dev.spinet.settings.SettingsRepository
import dev.spinet.widget.HeadlessPlayback
import dev.spinet.widget.WidgetSnapshot
import dev.spinet.widget.WidgetSnapshotStore
import dev.spinet.widget.logWidget
import dev.spinet.widget.refreshWidgets
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()

            .setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                 15_000,
                 60_000,
                 1_000,
                 2_000
            )
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)

            .setWakeMode(C.WAKE_MODE_NETWORK)

            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                 true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val queueAware = QueueAwarePlayer(player, applicationContext)

        queueAware.addListener(widgetPublisher)
        mediaSession = MediaSession.Builder(this, queueAware)

            .setBitmapLoader(DataSourceBitmapLoader(this))

            .setSessionActivity(openAppIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    private val widgetPublisher = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = publishToWidgets()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = publishToWidgets()
        override fun onPlaybackStateChanged(playbackState: Int) = publishToWidgets()
    }

    private val widgetSettings: SettingsRepository by lazy { SettingsRepository() }

    private fun publishToWidgets() {
        val player = mediaSession?.player ?: return

        if (player.currentMediaItem == null) {
            logWidget("service: playback event with nothing loaded, not publishing")
            return
        }
        val metadata = player.mediaMetadata
        val title = metadata.title?.toString().orEmpty()

        val previous = WidgetSnapshotStore.read(widgetSettings)
        val next = WidgetSnapshot(
            title = title,
            artist = metadata.artist?.toString().orEmpty(),
            artUrl = metadata.artworkUri?.toString().orEmpty(),
            trackKey = if (previous.title == title) previous.trackKey else "",
            isPlaying = player.isPlaying,
            hasSession = true,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it > 0L } ?: 0L,
            updatedAt = nowEpochMillis()
        )

        val unchanged = previous.copy(positionMs = 0L, updatedAt = 0L) ==
            next.copy(positionMs = 0L, updatedAt = 0L)
        if (unchanged) return

        logWidget("service: publishing '$title' playing=${player.isPlaying}")
        WidgetSnapshotStore.write(widgetSettings, next)
        refreshWidgets()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, dev.spinet.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

private class QueueAwarePlayer(
    player: Player,
    private val context: Context
) : ForwardingPlayer(player) {
    private val headlessScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val skipCommands = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
    )

    override fun getAvailableCommands(): Player.Commands {
        val builder = super.getAvailableCommands().buildUpon()
        skipCommands.forEach { builder.add(it) }
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        command in skipCommands || super.isCommandAvailable(command)

    override fun hasNextMediaItem(): Boolean = true
    override fun hasPreviousMediaItem(): Boolean = true

    override fun seekToNext() = skip(forward = true)

    override fun seekToNextMediaItem() = skip(forward = true)

    override fun seekToPrevious() = skip(forward = false)

    override fun seekToPreviousMediaItem() = skip(forward = false)

    private fun skip(forward: Boolean) {
        val handler = if (forward) MediaTransportBridge.onNext else MediaTransportBridge.onPrevious
        if (handler != null) {
            handler.invoke()
            return
        }
        headlessScope.launch {
            if (forward) HeadlessPlayback.next(context) else HeadlessPlayback.previous(context)
        }
    }
}
