package dev.spinet.widget

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import dev.spinet.audio.PlaybackService
import kotlin.coroutines.resume

internal object WidgetTransport {
    private suspend fun withActiveSession(
        context: Context,
        block: (MediaController) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        val controller = connect(context)
        if (controller == null) {
            WidgetLog.w("could not connect a MediaController to PlaybackService")
            return@withContext false
        }
        try {
            val idle = controller.playbackState == Player.STATE_IDLE
            WidgetLog.d(
                "session: item=${controller.currentMediaItem?.mediaMetadata?.title ?: "none"} " +
                    "state=${stateName(controller.playbackState)} playing=${controller.isPlaying}"
            )
            if (idle) return@withContext false
            block(controller)
            true
        } finally {
            controller.release()
        }
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    private suspend fun connect(context: Context): MediaController? {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        return suspendCancellableCoroutine { cont ->
            future.addListener(
                { cont.resume(runCatching { future.get() }.getOrNull()) },
                ContextCompat.getMainExecutor(app)
            )
            cont.invokeOnCancellation {
                runCatching { MediaController.releaseFuture(future) }
            }
        }
    }

    suspend fun togglePlayPause(context: Context): Boolean = withActiveSession(context) { c ->
        if (c.isPlaying) c.pause() else c.play()
    }

    suspend fun currentState(context: Context): LiveState? = withContext(Dispatchers.Main) {
        val controller = connect(context) ?: return@withContext null
        try {
            if (controller.playbackState == Player.STATE_IDLE) return@withContext null
            LiveState(
                title = controller.mediaMetadata.title?.toString().orEmpty(),
                artist = controller.mediaMetadata.artist?.toString().orEmpty(),
                isPlaying = controller.isPlaying,

                playWhenReady = controller.playWhenReady,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.takeIf { it > 0L } ?: 0L
            )
        } finally {
            controller.release()
        }
    }

    data class LiveState(
        val title: String,
        val artist: String,

        val isPlaying: Boolean,

        val playWhenReady: Boolean,
        val positionMs: Long,
        val durationMs: Long
    )

    suspend fun resumeInPlace(context: Context, seekToMs: Long?): Boolean =
        withActiveSession(context) { c ->
            if (seekToMs != null) c.seekTo(seekToMs)
            c.play()
        }

    suspend fun next(context: Context): Boolean = withActiveSession(context) { it.seekToNext() }

    suspend fun previous(context: Context): Boolean =
        withActiveSession(context) { it.seekToPrevious() }
}
