package dev.spinet.widget

import dev.spinet.data.savedServerSignIns
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.spinet.audio.PlaybackService
import dev.spinet.audio.setArtwork
import dev.spinet.data.NetworkRepository
import dev.spinet.data.createPlatformHttpClient
import dev.spinet.data.nowEpochMillis
import dev.spinet.library.LibraryRepository
import dev.spinet.model.Track
import dev.spinet.model.TrackSource
import dev.spinet.model.matchKey
import dev.spinet.plugins.PluginManager
import dev.spinet.settings.SettingsRepository
import dev.spinet.viewmodel.SessionSnapshot
import java.io.File
import kotlin.coroutines.resume

internal object HeadlessPlayback {
    private const val PLAYBACK_START_TIMEOUT_MS = 20_000L

    private const val POSITION_DRIFT_MS = 5_000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val json = Json { ignoreUnknownKeys = true }

    private val lock = Mutex()

    private val settings: SettingsRepository by lazy { SettingsRepository() }

    private val repository: NetworkRepository by lazy {
        val plugins = PluginManager(settings)
        NetworkRepository(
            client = createPlatformHttpClient(),
            sourcesProvider = { plugins.enabledOrdered() },
            extensionSources = { plugins.extensions.sources },
            serverSignIns = { settings.savedServerSignIns() }
        )
    }

    private val library: LibraryRepository by lazy { LibraryRepository(settings) }

    suspend fun resume(context: Context, awaitStart: Boolean = false): Boolean = lock.withLock {
        val session = newerOfLocalAndServer()
        val track = session.queue.getOrNull(session.index)
        if (track == null) {
            WidgetLog.w("headless resume: no saved session to resume")
            return@withLock false
        }

        val live = WidgetLog.guard("headless resume: read session") {
            WidgetTransport.currentState(context)
        }
        if (live != null && live.title.equals(track.title, ignoreCase = true)) {
            val drift = kotlin.math.abs(live.positionMs - session.positionMs)
            val seekTo = if (drift > POSITION_DRIFT_MS) session.positionMs else null
            WidgetLog.d(
                "headless resume: the session already holds '${track.title}'" +
                    if (seekTo != null) ", seeking to ${seekTo}ms (was ${live.positionMs}ms)" else ""
            )
            return@withLock WidgetTransport.resumeInPlace(context, seekTo)
        }
        play(context, session, session.index, session.positionMs, awaitStart)
    }

    private suspend fun newerOfLocalAndServer(): SessionSnapshot {
        val local = loadSession()

        val remote = WidgetServer.bookmark(settings) ?: return local
        val queue = remote.queue.ifEmpty { listOf(remote.track) }
        val index = remote.queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        WidgetLog.d("headless resume: taking the server bookmark from '${remote.device}'")
        return local.copy(
            queue = queue,
            index = index,
            positionMs = remote.positionMs,
            sourceQueue = remote.sourceQueue.ifEmpty { queue },
            shuffle = remote.shuffle,
            updatedAt = remote.updatedAt
        )
    }

    suspend fun next(context: Context, awaitStart: Boolean = false): Boolean =
        lock.withLock { step(context, forward = true, awaitStart = awaitStart) }

    suspend fun previous(context: Context, awaitStart: Boolean = false): Boolean =
        lock.withLock { step(context, forward = false, awaitStart = awaitStart) }

    private suspend fun step(context: Context, forward: Boolean, awaitStart: Boolean): Boolean {
        val live = WidgetLog.guard("headless skip: read session") {
            WidgetTransport.currentState(context)
        }
        val session = if (live != null) loadSession() else newerOfLocalAndServer()
        if (session.queue.isEmpty()) {
            WidgetLog.w("headless skip: queue is empty")
            return false
        }

        fun locate(title: String?): Int? = title?.takeIf { it.isNotBlank() }?.let { wanted ->
            session.queue.indexOfFirst { it.title.equals(wanted, ignoreCase = true) }
                .takeIf { it >= 0 }
        }
        val shown = WidgetRenderState.model.value.snapshot.title
        val liveIndex = locate(live?.title)
        val shownIndex = liveIndex ?: locate(shown)
        val from = shownIndex ?: session.index
        val source = when {
            liveIndex != null -> "the live session"
            shownIndex != null -> "the tile"
            else -> "the saved session"
        }

        val size = session.queue.size
        val target = ((from + if (forward) 1 else -1) % size + size) % size
        WidgetLog.d(
            "headless skip: ${if (forward) "next" else "previous"} from ${from + 1}/$size (from $source)"
        )
        return play(context, session, target, 0L, awaitStart)
    }

    private suspend fun play(
        context: Context,
        session: SessionSnapshot,
        index: Int,
        positionMs: Long,
        awaitStart: Boolean
    ): Boolean {
        val queued = session.queue.getOrNull(index) ?: return false
        val track = withContext(Dispatchers.IO) { playableCopyOf(context, queued) }
        WidgetLog.d("headless play: '${track.title}' (${index + 1}/${session.queue.size})")

        val url = try {
            withContext(Dispatchers.IO) { repository.resolveStreamUrl(track) }
        } catch (e: Exception) {
            WidgetLog.e("headless play: could not resolve '${track.title}'", e)
            return false
        }

        val started = withContext(Dispatchers.Main) {
            val controller = connect(context)
            if (controller == null) {
                WidgetLog.e("headless play: no MediaController")
                return@withContext false
            }
            try {
                val item = MediaItem.Builder()
                    .setUri(url)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtwork(track.thumbnailUrl)
                            .build()
                    )
                    .build()
                controller.setMediaItem(item)
                controller.prepare()
                if (positionMs > 0) controller.seekTo(positionMs)
                controller.play()

                persist(session.copy(index = index, positionMs = positionMs), track)

                if (awaitStart) {
                    val playing = try {
                        awaitPlaybackStart(controller)
                    } finally {
                        controller.release()
                    }
                    if (!playing) WidgetLog.w("headless play: '${track.title}' didn't start in time")
                    playing
                } else {
                    keepAliveUntilPlaying(controller, track.title)
                    true
                }
            } catch (e: Throwable) {
                controller.release()
                throw e
            }
        }
        return started
    }

    private fun keepAliveUntilPlaying(controller: MediaController, title: String) {
        scope.launch {
            try {
                if (!awaitPlaybackStart(controller)) {
                    WidgetLog.w("headless play: '$title' didn't start in time")
                }
            } finally {
                controller.release()
            }
        }
    }

    private suspend fun awaitPlaybackStart(controller: MediaController): Boolean =
        withTimeoutOrNull(PLAYBACK_START_TIMEOUT_MS) {
            if (controller.isPlaying) return@withTimeoutOrNull true
            suspendCancellableCoroutine { cont ->
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if (isPlaying && !cont.isCompleted) {
                            controller.removeListener(this)
                            cont.resume(true)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        if (!cont.isCompleted) {
                            WidgetLog.e("headless play: player error", error)
                            controller.removeListener(this)
                            cont.resume(false)
                        }
                    }
                }
                controller.addListener(listener)
                cont.invokeOnCancellation { runCatching { controller.removeListener(listener) } }
            }
        } ?: false

    private fun playableCopyOf(context: Context, track: Track): Track {
        if (track.source != TrackSource.LOCAL) return track
        if (isReadable(context, track.streamUrl)) return track

        val key = track.matchKey()
        val alternative = (library.favorites() + library.playlists().flatMap { it.tracks })
            .firstOrNull { it.matchKey() == key && it.source != TrackSource.LOCAL }
        if (alternative == null) {
            WidgetLog.w("'${track.title}' is a local file this device doesn't have, and no streamable copy exists")
            return track
        }
        WidgetLog.d("'${track.title}': local file missing here, using a streamable copy")

        return if (alternative.thumbnailUrl.isBlank()) {
            alternative.copy(thumbnailUrl = track.thumbnailUrl)
        } else {
            alternative
        }
    }

    private fun isReadable(context: Context, streamUrl: String): Boolean {
        if (streamUrl.isBlank()) return false
        return runCatching {
            if (streamUrl.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(streamUrl))?.use { true } ?: false
            } else {
                File(streamUrl.removePrefix("file://")).canRead()
            }
        }.getOrDefault(false)
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
            cont.invokeOnCancellation { runCatching { MediaController.releaseFuture(future) } }
        }
    }

    private fun persist(session: SessionSnapshot, track: Track) {
        runCatching {
            settings.lastSessionJson = json.encodeToString(session.copy(updatedAt = nowEpochMillis()))
        }
        WidgetSnapshotStore.write(
            settings,
            WidgetSnapshot(
                title = track.title,
                artist = track.artist,
                artUrl = track.thumbnailUrl,
                trackKey = track.matchKey(),
                isPlaying = true,
                hasSession = true,
                updatedAt = nowEpochMillis()
            )
        )
        refreshWidgets()
    }

    private fun loadSession(): SessionSnapshot =
        runCatching {
            json.decodeFromString<SessionSnapshot>(settings.lastSessionJson.ifBlank { "{}" })
        }.getOrDefault(SessionSnapshot())

    private fun findTrack(key: String): Track? {
        fun List<Track>.hit() = firstOrNull { it.matchKey() == key }
        return library.favorites().hit()
            ?: library.playlists().firstNotNullOfOrNull { it.tracks.hit() }
            ?: loadSession().queue.hit()
    }
}
