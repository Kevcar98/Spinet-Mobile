package dev.spinet.widget

import android.content.Context
import android.graphics.Bitmap
import dev.spinet.model.NowPlayingState
import dev.spinet.model.matchKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object WidgetRenderState {
    data class Model(
        val snapshot: WidgetSnapshot = WidgetSnapshot(),
        val art: Bitmap? = null,
        val backdrop: Bitmap? = null,

        val ring: Bitmap? = null
    )

    private val refreshLock = Mutex()

    private val _model = MutableStateFlow(Model())
    val model: StateFlow<Model> = _model.asStateFlow()

    private fun WidgetSnapshot.mergedWith(live: WidgetTransport.LiveState): WidgetSnapshot =
        copy(
            title = live.title.ifBlank { title },
            artist = live.artist.ifBlank { artist },

            isPlaying = live.playWhenReady,
            hasSession = true,
            positionMs = live.positionMs,
            durationMs = live.durationMs
        )

    private fun WidgetSnapshot.fromRemote(remote: NowPlayingState): WidgetSnapshot {
        val key = remote.track.matchKey()

        val art = remote.track.thumbnailUrl.ifBlank {
            WidgetLog.guard("render state: local art") { WidgetData.localCopyOf(key) }
                ?.thumbnailUrl.orEmpty()
        }
        return copy(
            title = remote.track.title,
            artist = remote.track.artist,
            artUrl = art,
            trackKey = key,

            isPlaying = false,
            hasSession = true,
            positionMs = remote.positionMs,
            durationMs = remote.durationMs,
            updatedAt = remote.updatedAt
        )
    }

    private var loadedArtUrl: String? = null

    suspend fun refreshLocal(context: Context) {
        val stored = WidgetLog.guard("render state: read snapshot") { WidgetData.snapshot() }
            ?: WidgetSnapshot()
        val live = WidgetLog.guard("render state: read session") {
            WidgetTransport.currentState(context)
        }
        publish(context, if (live != null) stored.mergedWith(live) else stored)
    }

    suspend fun refresh(context: Context, repaint: suspend () -> Unit = {}) =
        refreshLock.withLock { refreshInner(context, repaint) }

    private suspend fun refreshInner(context: Context, repaint: suspend () -> Unit) {
        val stored = WidgetLog.guard("render state: read snapshot") { WidgetData.snapshot() }
            ?: WidgetSnapshot()
        val live = WidgetLog.guard("render state: read session") {
            WidgetTransport.currentState(context)
        }

        val locallyAudible = live != null && live.playWhenReady
        WidgetLog.d(
            "refresh: stored='${stored.title}' storedAt=${stored.updatedAt} | " +
                "live=${live?.let { "'${it.title}' playing=${it.isPlaying}" } ?: "none"} | " +
                "locallyAudible=$locallyAudible"
        )

        val localSnapshot = if (live != null) stored.mergedWith(live) else stored
        WidgetLog.d("refresh: source=${if (live != null) "LIVE" else "LOCAL-STORED"} (no network)")
        publish(context, localSnapshot, repaint)
        repaint()

        if (locallyAudible) return
        val remote = WidgetLog.guard("render state: server bookmark") {
            WidgetServer.bookmark(WidgetData.settings(), compact = true)
        } ?: return
        WidgetLog.d(
            "refresh: source=SERVER device='${remote.device}' " +
                "track='${remote.track.title}' pos=${remote.positionMs} at=${remote.updatedAt}"
        )
        val remoteSnapshot = stored.fromRemote(remote)
        if (remoteSnapshot != localSnapshot) {
            publish(context, remoteSnapshot, repaint)
            repaint()
        }
    }

    private suspend fun publish(
        context: Context,
        snapshot: WidgetSnapshot,
        repaint: suspend () -> Unit = {}
    ) {
        val current = _model.value
        var art = current.art
        var backdrop = current.backdrop

        val artIsStale = snapshot.artUrl != loadedArtUrl
        if (artIsStale && snapshot.artUrl.isBlank()) {
            art = null
            backdrop = null
            loadedArtUrl = ""
        }

        pushModel(snapshot, art, backdrop)
        if (!artIsStale || snapshot.artUrl.isBlank()) return
        repaint()

        val loaded = withTimeoutOrNull(ART_BUDGET_MS) {
            WidgetLog.guard("render state: load art") {
                WidgetArt.loadPair(context, snapshot.artUrl)
            }
        }
        val loadedArt = loaded?.first
        val loadedBackdrop = loaded?.second
        if (loadedArt == null && loadedBackdrop == null) {
            WidgetLog.w("render state: art load failed, keeping the last one")
            return
        }
        loadedArtUrl = snapshot.artUrl
        pushModel(snapshot, loadedArt, loadedBackdrop)
        repaint()
    }

    private const val ART_BUDGET_MS = 2_500L

    private fun pushModel(snapshot: WidgetSnapshot, art: Bitmap?, backdrop: Bitmap?) {
        WidgetLog.d(
            "refresh: drawing '${snapshot.title}' by '${snapshot.artist}' " +
                "playing=${snapshot.isPlaying} hasSession=${snapshot.hasSession} " +
                "art=${art != null}"
        )

        val ring = snapshot.progress?.let {
            WidgetLog.guard("render state: draw ring") {
                WidgetArt.progressRing(it, onArt = backdrop != null)
            }
        }

        val next = Model(snapshot, art, backdrop, ring)
        if (next != _model.value) _model.value = next
    }
}
