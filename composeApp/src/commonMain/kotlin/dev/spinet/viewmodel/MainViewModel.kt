package dev.spinet.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.spinet.AppBranding
import dev.spinet.audio.AudioPlayer
import dev.spinet.audio.PlaybackState
import dev.spinet.audio.TransportCommand
import dev.spinet.data.ServerAccess
import dev.spinet.data.BrowsePlaylistDto
import dev.spinet.data.ImportedSource
import dev.spinet.data.ImportedTheme
import dev.spinet.data.NetworkRepository
import dev.spinet.data.nowEpochMillis
import dev.spinet.data.PlaylistImportResult
import dev.spinet.data.PluginBundle
import dev.spinet.util.openUrlExternally
import dev.spinet.data.SearchUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import dev.spinet.download.DownloadResult
import dev.spinet.download.FileDownloader
import dev.spinet.download.TransferController
import dev.spinet.download.TransferNotifier
import dev.spinet.library.LibraryRepository
import dev.spinet.local.LocalFolder
import dev.spinet.local.deleteLocalTrack
import dev.spinet.local.isLocalFileReadable
import dev.spinet.local.listLocalTracks
import dev.spinet.local.readLocalTrackBytes
import dev.spinet.local.writeLocalTrackBytes
import dev.spinet.model.Playlist
import dev.spinet.model.NowPlayingState
import dev.spinet.model.PlaylistSyncState
import dev.spinet.model.looseKey
import dev.spinet.model.matchKey
import dev.spinet.data.FIRST_SERVER_ID
import dev.spinet.data.forgetSignIn
import dev.spinet.data.saveSignIn
import dev.spinet.data.signInFor
import dev.spinet.model.SourceKind
import dev.spinet.model.SourcePlugin
import dev.spinet.model.ThemeSpec
import dev.spinet.model.Track
import dev.spinet.model.TrackSource
import dev.spinet.plugins.PluginManager
import dev.spinet.recognition.CaptureSource
import dev.spinet.recognition.IdentifyPhase
import dev.spinet.recognition.RecognizedSong
import dev.spinet.recognition.createAudioCapture
import dev.spinet.settings.SettingsRepository
import dev.spinet.theme.ThemeRegistry
import dev.spinet.widget.WidgetSnapshot
import dev.spinet.widget.WidgetSnapshotStore
import dev.spinet.widget.logWidget
import dev.spinet.widget.refreshWidgets
import dev.spinet.util.AudioContainer
import dev.spinet.update.UpdateInstaller
import dev.spinet.update.UpdateChecker
import dev.spinet.update.AvailableUpdate
import dev.spinet.local.isUsableArt
import dev.spinet.local.portableArt

@kotlinx.serialization.Serializable
enum class RepeatMode { OFF, ALL, ONE }

@kotlinx.serialization.Serializable
internal data class SessionSnapshot(
    val queue: List<Track> = emptyList(),
    val index: Int = -1,
    val positionMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,

    val sourceQueue: List<Track> = emptyList(),

    val history: List<Track> = emptyList(),

    val updatedAt: Long = 0L
)

data class ServerEntry(
    val id: String,
    val name: String,
    val baseUrl: String,
    val username: String,
    val password: String,
    val enabled: Boolean
)

data class AppUiState(
    val searchQuery: String = "",
    val searchResults: List<Track> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,

    val identifyPhase: IdentifyPhase = IdentifyPhase.IDLE,
    val identifyResult: RecognizedSong? = null,
    val identifyError: String? = null,
    val identifySources: List<CaptureSource> = emptyList(),
    val downloadStatus: String? = null,
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val favoriteTrackIds: Set<String> = emptySet(),

    val serverLibraryKeys: Set<String> = emptySet(),
    val currentTrack: Track? = null,

    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,

    val sourceQueue: List<Track> = emptyList(),

    val shuffle: Boolean = false,

    val repeatMode: RepeatMode = RepeatMode.OFF,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val experimentalPluginEnabled: Boolean = false,
    val highQualityArt: Boolean = true,
    val sources: List<SourcePlugin> = emptyList(),
    val backendTestStatus: String? = null,
    val themes: List<ThemeSpec> = emptyList(),
    val selectedThemeId: String = "",
    val importStatus: String? = null,
    val playlistImportStatus: String? = null,
    val playlistImportPreview: PlaylistImportResult? = null,

    val versionPickerFor: Track? = null,
    val versionOptions: List<Track> = emptyList(),
    val versionLoading: Boolean = false,
    val playback: PlaybackState = PlaybackState.IDLE,

    val browsePlaylists: List<BrowsePlaylistDto> = emptyList(),

    val browseTracks: List<Track> = emptyList(),
    val browseLoading: Boolean = false,
    val browseError: String? = null,

    val localFolderLabel: String = "",
    val localTracks: List<Track> = emptyList(),
    val localLoading: Boolean = false,

    val serverHost: String = "",

    val autoSyncEnabled: Boolean = false,

    val themeSyncEnabled: Boolean = true,

    val selectedTrackIds: Set<String> = emptySet(),

    val availableUpdate: AvailableUpdate? = null,

    val updateDismissed: Boolean = false,

    val updateDialogOpen: Boolean = false,

    val updateBusy: Boolean = false,

    val updateError: String? = null,

    val playbackSyncEnabled: Boolean = true,

    val servers: List<ServerEntry> = emptyList(),
    val serverConfigStatus: String? = null,

    val defaultServerDir: String = "",

    val serverFolders: List<String> = emptyList(),
    val serverFoldersLoading: Boolean = false,

    val transferProgress: Float? = null,
    val transferLabel: String? = null,

    val transferQueueDepth: Int = 0,

    val settingsSectionOrder: List<String> = emptyList(),
    val settingsLocked: Boolean = true,

    val sleepTimerRemainingMs: Long? = null,

    val sleepAtEndOfTrack: Boolean = false,

    val searchHistory: List<String> = emptyList(),

    val mostPlayed: List<Track> = emptyList(),

    val playCounts: Map<String, Int> = emptyMap(),

    val hasPrevious: Boolean = false
)

@kotlinx.serialization.Serializable
data class PlayStat(val count: Int, val track: Track, val resetAt: Long = 0)

private fun List<Track>.distinctById(): List<Track> {
    val seen = HashSet<String>()
    return filter { it.id.isBlank() || seen.add(it.id) }
}

private fun collapseStats(stats: MutableMap<String, PlayStat>): MutableMap<String, PlayStat> {
    val byId = HashMap<String, String>()
    val out = LinkedHashMap<String, PlayStat>()
    for ((key, stat) in stats.entries.sortedByDescending { it.value.count }) {
        val id = stat.track.id
        if (id.isBlank()) {
            out[key] = stat
            continue
        }
        val kept = byId[id]
        if (kept == null) {
            byId[id] = key
            out[key] = stat
        } else {
            val existing = out.getValue(kept)
            out[kept] = existing.copy(
                count = existing.count + stat.count,
                resetAt = maxOf(existing.resetAt, stat.resetAt)
            )
        }
    }
    return out
}

private enum class TransferOutcome { OK, FAILED, SKIPPED }

class MainViewModel(
    private val audioPlayer: AudioPlayer,
    private val repository: NetworkRepository,
    private val settingsRepository: SettingsRepository,
    private val pluginManager: PluginManager,
    private val themeRegistry: ThemeRegistry,
    private val fileDownloader: FileDownloader,
    private val libraryRepository: LibraryRepository,

    private val updateInstaller: UpdateInstaller? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUiState(
            playlists = libraryRepository.playlists(),
            favorites = libraryRepository.favorites(),
            favoriteTrackIds = libraryRepository.favorites().map { it.id }.toSet(),
            experimentalPluginEnabled = settingsRepository.experimentalPluginEngineEnabled,
            highQualityArt = settingsRepository.highQualityArtEnabled,
            searchHistory = settingsRepository.searchHistoryRaw
                .split("\n").map { it.trim() }.filter { it.isNotBlank() },
            sources = pluginManager.all(),
            themes = themeRegistry.all(),
            selectedThemeId = themeRegistry.selectedId(),
            localFolderLabel = settingsRepository.localFolderLabel,
            serverHost = libraryServerUrl().orEmpty(),
            servers = serverEntries(),
            defaultServerDir = settingsRepository.defaultServerDir,
            settingsSectionOrder = settingsRepository.settingsOrderCsv
                .split(",").map { it.trim() }.filter { it.isNotBlank() },
            settingsLocked = settingsRepository.settingsLocked,
            autoSyncEnabled = settingsRepository.syncAutoEnabled,
            themeSyncEnabled = settingsRepository.syncThemeEnabled,
            playbackSyncEnabled = settingsRepository.syncPlaybackEnabled
        )
    )
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private companion object {
        const val MAX_FAILED_IN_A_ROW = 3

        val SERVER_DOWN_FOR = kotlin.time.Duration.parse("2m")

        const val UPLOAD_RETRY_DELAY_MS = 3_000L

        const val SERVER_RECHECK_MS = 30_000L

        val UPLOAD_MARK_KEPT = kotlin.time.Duration.parse("10m")

        const val QUEUE_WINDOW_BEHIND = 10

        const val QUEUE_WINDOW_TOTAL = 60

        const val DEVICE_NAME = "phone"
    }

    private var searchJob: Job? = null

    private val sessionJson = Json { ignoreUnknownKeys = true }

    private var needsRestore = false
    private var restoredPositionMs = 0L
    private var lastSessionSaveMs = 0L

    private var pendingResumeSeekMs = 0L

    private fun saveSession() {
        val s = _uiState.value
        if (s.currentTrack == null || s.queue.isEmpty()) return
        val now = nowEpochMillis()
        val snap = SessionSnapshot(
            queue = s.queue,
            index = s.queueIndex,
            positionMs = s.positionMs,
            shuffle = s.shuffle,
            repeat = s.repeatMode,
            sourceQueue = s.sourceQueue,
            history = historyWindow(),
            updatedAt = now
        )
        runCatching { settingsRepository.lastSessionJson = sessionJson.encodeToString(snap) }
        pushNowPlayingToServer(s.currentTrack, s.positionMs, s.durationMs, now)
    }

    private fun playbackSyncUrl(): String? =
        if (settingsRepository.syncPlaybackEnabled) libraryServerUrl() else null

    private fun pushNowPlayingToServer(track: Track, positionMs: Long, durationMs: Long, updatedAt: Long) {
        val base = playbackSyncUrl() ?: return
        val s = _uiState.value
        val playing = s.playback == PlaybackState.PLAYING || s.playback == PlaybackState.BUFFERING
        val window = queueWindow(s.queue, s.queueIndex)
        viewModelScope.launch {
            repository.putNowPlaying(
                base,
                NowPlayingState(
                    track = track.copy(thumbnailUrl = shareableArtUrl(track)),
                    positionMs = positionMs,
                    durationMs = durationMs,

                    updatedAt = 0L,
                    device = DEVICE_NAME,
                    isPlaying = playing,
                    queue = window.queue,
                    queueIndex = window.index,

                    sourceQueue = emptyList(),
                    history = historyWindow(),
                    shuffle = s.shuffle,
                    repeat = s.repeatMode.name
                )
            )
        }
    }

    private data class QueueWindow(val queue: List<Track>, val index: Int)

    private fun queueWindow(queue: List<Track>, index: Int): QueueWindow {
        if (queue.size <= QUEUE_WINDOW_TOTAL) {
            return QueueWindow(queue, index.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
        }
        val safeIndex = index.coerceIn(0, queue.size - 1)
        val start = (safeIndex - QUEUE_WINDOW_BEHIND).coerceIn(0, queue.size - QUEUE_WINDOW_TOTAL)
        return QueueWindow(
            queue = queue.subList(start, start + QUEUE_WINDOW_TOTAL).toList(),
            index = safeIndex - start
        )
    }

    private fun shareableArtUrl(track: Track): String {
        val art = track.thumbnailUrl
        if (art.startsWith("http://", true) || art.startsWith("https://", true)) return art
        val base = track.serverBaseUrl.trim().trimEnd('/')
        return if (base.isNotBlank() && track.id.isNotBlank()) "$base/art/${track.id}" else ""
    }

    private fun restoreSession() {
        val json = settingsRepository.lastSessionJson
        val snap = runCatching { sessionJson.decodeFromString<SessionSnapshot>(json.ifBlank { "{}" }) }.getOrNull()
        val track = snap?.queue?.getOrNull(snap.index)
        if (track != null) {
            needsRestore = true
            restoredPositionMs = snap.positionMs

            playHistory.clear()
            playHistory.addAll(snap.history.takeLast(HISTORY_WINDOW))
            _uiState.value = _uiState.value.copy(
                currentTrack = track,
                queue = snap.queue,
                queueIndex = snap.index,
                sourceQueue = snap.sourceQueue.ifEmpty { snap.queue },
                shuffle = snap.shuffle,
                repeatMode = snap.repeat,
                positionMs = snap.positionMs,
                durationMs = 0L,
                playback = PlaybackState.PAUSED
            )
        }

        publishHasPrevious()
        maybeRestoreFromServer(localUpdatedAt = snap?.updatedAt ?: 0L)
    }

    private fun maybeRestoreFromServer(localUpdatedAt: Long) {
        viewModelScope.launch { pullRemoteSession(localUpdatedAt) }
    }

    private suspend fun pullRemoteSession(localUpdatedAt: Long) {
        val base = playbackSyncUrl() ?: return
        run {
            val remote = repository.getNowPlaying(base, withHistory = true) ?: return

            if (remote.updatedAt <= localUpdatedAt) return
            if (!needsRestore && _uiState.value.playback == PlaybackState.PLAYING) return

            val serverByKey = runCatching { repository.browsePlaylistTracks(base, "__all__") }
                .getOrDefault(emptyList())
                .associateBy { it.matchKey() }
            fun mapped(t: Track): Track = serverByKey[t.matchKey()] ?: t
            val resume = mapped(remote.track)
            val queue = remote.queue.map { mapped(it) }.ifEmpty { listOf(resume) }
            val source = remote.sourceQueue.map { mapped(it) }.ifEmpty { queue }

            playHistory.clear()
            playHistory.addAll(remote.history.map { mapped(it) }.takeLast(HISTORY_WINDOW))
            val idx = remote.queueIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
            val repeat = runCatching { RepeatMode.valueOf(remote.repeat) }.getOrDefault(RepeatMode.OFF)
            needsRestore = true
            restoredPositionMs = remote.positionMs
            _uiState.value = _uiState.value.copy(
                currentTrack = resume,
                queue = queue,
                queueIndex = idx,
                sourceQueue = source,
                shuffle = remote.shuffle,
                repeatMode = repeat,
                positionMs = remote.positionMs,
                durationMs = 0L,
                playback = PlaybackState.PAUSED
            )
            publishHasPrevious()
        }
    }

    private val statsJson = Json { ignoreUnknownKeys = true }

    private val HISTORY_WINDOW = 50

    private val playHistory = ArrayDeque<Track>()

    private var serverLibraryTracks: Map<String, Track> = emptyMap()

    private var serverCopies: Map<String, List<Track>> = emptyMap()

    private val downServers = mutableMapOf<String, kotlin.time.TimeMark>()

    private fun serverIsDown(base: String): Boolean =
        downServers[base.trimEnd('/')]?.let { it.elapsedNow() < SERVER_DOWN_FOR } ?: false

    private fun markServerDown(base: String) {
        println("[MainViewModel] server $base didn't answer; trying the next one for a while")
        downServers[base.trimEnd('/')] = kotlin.time.TimeSource.Monotonic.markNow()
    }

    private fun serverCopyFor(key: String): Track? {
        val copies = serverCopies[key] ?: return serverLibraryTracks[key]
        return copies.firstOrNull { !serverIsDown(it.serverBaseUrl) } ?: copies.firstOrNull()
    }

    private suspend fun resolveWithServerFallback(track: Track): Pair<Track, String> {
        val stale = track.serverBaseUrl.isNotBlank() &&
            (serverIsDown(track.serverBaseUrl) || track.serverBaseUrl.trimEnd('/') !in serverBasesInOrder())
        try {
            if (stale) throw SearchUnavailableException("This song's server isn't available.", null)
            return track to repository.resolveStreamUrl(track)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (track.serverBaseUrl.isBlank()) throw e
            if (!stale) markServerDown(track.serverBaseUrl)
            val others = serverCopies[songMatchKey(track)].orEmpty().filter {
                it.serverBaseUrl.trimEnd('/') != track.serverBaseUrl.trimEnd('/') && !serverIsDown(it.serverBaseUrl)
            }
            for (copy in others) {
                try {
                    return copy to repository.resolveStreamUrl(copy)
                } catch (c: CancellationException) {
                    throw c
                } catch (_: Exception) {
                    markServerDown(copy.serverBaseUrl)
                }
            }
            val original = originalForCurrent
                ?.takeIf { it.serverBaseUrl.isBlank() && it.source != TrackSource.LOCAL && songMatchKey(it) == songMatchKey(track) }
                ?: throw e
            return original to repository.resolveStreamUrl(original)
        }
    }

    private val playStats: MutableMap<String, PlayStat> = collapseStats(
        runCatching {
            statsJson.decodeFromString<MutableMap<String, PlayStat>>(
                settingsRepository.playStatsJson.ifBlank { "{}" }
            )
        }.getOrDefault(mutableMapOf())
    )

    private val audioCapture = createAudioCapture()
    private var identifyJob: Job? = null

    init {
        publishMostPlayed()
        _uiState.value = _uiState.value.copy(
            identifySources = runCatching { audioCapture?.sources() }.getOrNull().orEmpty()
        )
        viewModelScope.launch {
            audioPlayer.playbackState.collect { state ->
                _uiState.value = _uiState.value.copy(playback = state)

                if (state == PlaybackState.PLAYING || state == PlaybackState.BUFFERING) {
                    needsRestore = false
                }
                if (state == PlaybackState.PLAYING) failedInARow = 0
                if (state == PlaybackState.PAUSED) saveSession()
                if (state == PlaybackState.ENDED) onSongEnded()
                if (state == PlaybackState.ERROR) onPlaybackError()
            }
        }
        viewModelScope.launch {
            audioPlayer.positionMs.collect { pos ->
                _uiState.value = _uiState.value.copy(positionMs = pos)

                val now = pos
                if (now - lastSessionSaveMs > 5_000L || now < lastSessionSaveMs) {
                    lastSessionSaveMs = now
                    if (_uiState.value.playback == PlaybackState.PLAYING) saveSession()
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.durationMs.collect { dur ->
                _uiState.value = _uiState.value.copy(durationMs = dur)

                if (dur > 0 && pendingResumeSeekMs > 0 && pendingResumeSeekMs < dur) {
                    audioPlayer.seekTo(pendingResumeSeekMs)
                    pendingResumeSeekMs = 0L
                } else if (dur > 0) {
                    pendingResumeSeekMs = 0L
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.nowPlaying.collect { info ->
                if (info != null && _uiState.value.currentTrack == null) {
                    _uiState.value = _uiState.value.copy(
                        currentTrack = Track(
                            id = "restored:${info.title}",
                            title = info.title,
                            artist = info.artist,
                            durationLabel = "",
                            thumbnailUrl = info.artworkUrl.orEmpty(),
                            source = TrackSource.ONLINE,
                            itemId = "",
                            streamUrl = "",
                            downloadable = false
                        )
                    )
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.transportCommands.collect { command ->
                when (command) {
                    TransportCommand.NEXT -> onNext()
                    TransportCommand.PREVIOUS -> onPrevious()
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.autoAdvanced.collect { onAutoAdvanced() }
        }

        viewModelScope.launch {
            _uiState
                .map { s ->
                    val track = s.currentTrack
                    WidgetSnapshot(
                        title = track?.title.orEmpty(),
                        artist = track?.artist.orEmpty(),
                        artUrl = track?.thumbnailUrl.orEmpty(),
                        trackKey = track?.let { songMatchKey(it) }.orEmpty(),
                        isPlaying = s.playback == PlaybackState.PLAYING,
                        hasSession = track != null,
                        positionMs = s.positionMs,
                        durationMs = s.durationMs
                    )
                }

                .distinctUntilChanged { old, new ->
                    old.copy(positionMs = 0L, durationMs = 0L) ==
                        new.copy(positionMs = 0L, durationMs = 0L)
                }
                .collect { snapshot ->

                    try {
                        WidgetSnapshotStore.write(
                            settingsRepository,
                            snapshot.copy(updatedAt = nowEpochMillis())
                        )
                        refreshWidgets()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logWidget("snapshot update failed: ${e.message ?: e}")
                    }
                }
        }

        runSearch("")

        refreshServerLibrary()

        if (settingsRepository.localFolderUri.isNotBlank()) refreshLocalTracks()

        restoreSession()

        if (settingsRepository.syncAutoEnabled) {
            libraryServerUrl()?.let { base ->
                viewModelScope.launch {
                    repository.getPlaylistSync(base)?.let { applyRemoteSync(it) }
                }
            }
        }
    }

    fun onPickLocalFolder(folder: LocalFolder) {
        settingsRepository.localFolderUri = folder.uri
        settingsRepository.localFolderLabel = folder.label
        _uiState.value = _uiState.value.copy(localFolderLabel = folder.label)
        refreshLocalTracks()
    }

    fun refreshLocalTracks() {
        viewModelScope.launch { scanLocalTracks() }
    }

    private suspend fun scanLocalTracks(showLoading: Boolean = true) {
        val uri = settingsRepository.localFolderUri
        if (uri.isBlank()) {
            _uiState.value = _uiState.value.copy(localTracks = emptyList(), localFolderLabel = "")
            return
        }
        if (showLoading) _uiState.value = _uiState.value.copy(localLoading = true)
        val tracks = listLocalTracks(uri)
        _uiState.value = _uiState.value.copy(
            localTracks = withKnownArtwork(tracks),
            localLoading = false
        )
    }

    private suspend fun repairBrokenDownloads() {
        val suspects = _uiState.value.localTracks.filter {
            it.streamUrl.contains(".mp3", ignoreCase = true) ||
                it.streamUrl.contains("%2Emp3", ignoreCase = true)
        }
        if (suspects.isEmpty()) return
        var repaired = 0
        var healthy = 0
        var failed = 0
        val odd = ArrayList<String>()
        for (t in suspects) {
            val bytes = runCatching { readLocalTrackBytes(t.streamUrl) }.getOrNull()
            if (bytes == null || bytes.size < 26) { failed++; continue }

            var offset = 0
            while (offset + 10 <= bytes.size &&
                bytes[offset] == 'I'.code.toByte() && bytes[offset + 1] == 'D'.code.toByte() &&
                bytes[offset + 2] == '3'.code.toByte()
            ) {
                val size = ((bytes[offset + 6].toInt() and 0x7F) shl 21) or
                    ((bytes[offset + 7].toInt() and 0x7F) shl 14) or
                    ((bytes[offset + 8].toInt() and 0x7F) shl 7) or
                    (bytes[offset + 9].toInt() and 0x7F)
                val next = offset + 10 + size + if ((bytes[offset + 5].toInt() and 0x10) != 0) 10 else 0
                if (next <= offset || next > bytes.size) break
                offset = next
            }
            if (offset == 0) { healthy++; continue }
            val isMp4After = offset + 8 <= bytes.size &&
                bytes[offset + 4] == 'f'.code.toByte() && bytes[offset + 5] == 't'.code.toByte() &&
                bytes[offset + 6] == 'y'.code.toByte() && bytes[offset + 7] == 'p'.code.toByte()
            val isMp3After = offset + 2 <= bytes.size &&
                (bytes[offset].toInt() and 0xFF) == 0xFF && (bytes[offset + 1].toInt() and 0xE0) == 0xE0
            when {
                isMp4After -> {
                    val stripped = bytes.copyOfRange(offset, bytes.size)
                    if (fileDownloader.saveRepairedAudio(stripped, t.title, t.artist, t.localFolder)) {
                        runCatching { deleteLocalTrack(t.streamUrl) }
                        repaired++
                    } else {
                        failed++
                    }
                }
                isMp3After -> healthy++
                else -> {
                    val hex = bytes.copyOfRange(offset, minOf(offset + 8, bytes.size))
                        .joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                    odd.add("${t.title} [$hex]")
                }
            }
        }
        val summary = buildString {
            append("Checked ${suspects.size} mp3 file(s): $repaired repaired, $healthy ok")
            if (failed > 0) append(", $failed failed")
            if (odd.isNotEmpty()) append(", ${odd.size} unknown: ${odd.take(3).joinToString("; ")}")
        }
        _uiState.value = _uiState.value.copy(downloadStatus = summary)
        if (repaired > 0) scanLocalTracks()
    }

    fun onRefreshLocalWithMetadata() {
        enqueueTransfer {
            scanLocalTracks()
            repairBrokenDownloads()

            val missing = _uiState.value.localTracks.filter {
                needsArtUpgrade(it.thumbnailUrl) || it.artist == "Unknown artist"
            }
            if (missing.isEmpty()) return@enqueueTransfer
            val knownArt = knownArtworkByKey()
            beginTransfer("Fetching music info…")
            val (ok, _, _) = runParallelTransfer(missing, "Tagging", concurrency = 2) { track ->
                val artist = track.artist.takeIf { it != "Unknown artist" }.orEmpty()
                val info = repository.lookupTrackInfo(artist, track.title)

                val artUrl = info?.artworkUrl?.takeIf { it.isNotBlank() }
                    ?: knownArt[track.matchKey()]
                    ?: return@runParallelTransfer TransferOutcome.SKIPPED
                val newTitle = info?.title?.ifBlank { track.title } ?: track.title
                val newArtist = info?.artist?.ifBlank { track.artist } ?: track.artist
                if (fileDownloader.saveLocalArtwork(track.streamUrl, newTitle, newArtist, artUrl)) {
                    TransferOutcome.OK
                } else {
                    TransferOutcome.FAILED
                }
            }
            finishTransfer("Updated $ok file(s).")
            scanLocalTracks()
            refreshCurrentTrackArtwork()
        }
    }

    fun onRefreshTracksMetadata(tracks: List<Track>, force: Boolean = false) {
        enqueueTransfer {
            println("[MetaRefresh] checking ${tracks.size} track(s)")
            val missing = if (force) tracks else tracks.filter {
                needsArtUpgrade(it.thumbnailUrl) || it.artist.isBlank() || it.artist == "Unknown artist"
            }
            println(
                "[MetaRefresh] ${missing.size} missing art/artist: " +
                    missing.joinToString { "'${it.title}'" }
            )
            if (missing.isEmpty()) {
                beginTransfer("Checking metadata…")
                finishTransfer("Metadata already up to date (${tracks.size} track(s) have art + artist).")
                return@enqueueTransfer
            }
            beginTransfer("Fetching music info…")
            val knownArt = knownArtworkByKey()
            val statLock = Mutex()
            val (ok, _, _) = runParallelTransfer(missing, "Tagging", concurrency = 2) { track ->
                val artist = track.artist.takeIf { it.isNotBlank() && it != "Unknown artist" }.orEmpty()
                val info = repository.lookupTrackInfo(artist, track.title)

                val artUrl = info?.artworkUrl?.takeIf { it.isNotBlank() }
                    ?: knownArt[track.matchKey()]
                if (artUrl == null) {
                    println("[MetaRefresh] no cover found for '${track.artist} - ${track.title}'")
                    return@runParallelTransfer TransferOutcome.SKIPPED
                }
                val merged = track.copy(
                    title = info?.title?.ifBlank { track.title } ?: track.title,
                    artist = info?.artist?.ifBlank { track.artist } ?: track.artist,
                    thumbnailUrl = artUrl
                )
                libraryRepository.replaceInPlaylists(track.id, merged)
                libraryRepository.replaceInFavorites(track.id, merged)

                statLock.withLock { applyStatRename(track, merged) }
                TransferOutcome.OK
            }
            println("[MetaRefresh] done, updated $ok")
            runCatching { settingsRepository.playStatsJson = statsJson.encodeToString(playStats) }
            publishMostPlayed()
            finishTransfer("Updated $ok track(s).")
            refreshLibrary()
            refreshCurrentTrackArtwork()
        }
    }

    private fun knownArtworkByKey(): Map<String, String> = artworkIndex(
        _uiState.value.playlists.flatMap { it.tracks } +
            _uiState.value.favorites +
            _uiState.value.localTracks
    )

    private fun artworkIndex(tracks: List<Track>): Map<String, String> {
        val known = mutableMapOf<String, String>()
        for (t in tracks) {
            val art = t.thumbnailUrl
            if (art.isNotBlank() && isUsableArt(art) && !known.containsKey(t.matchKey())) {
                known[t.matchKey()] = art
            }
        }
        return known
    }

    private fun fillArt(track: Track, known: Map<String, String>): Track {
        if (track.thumbnailUrl.isNotBlank() && isUsableArt(track.thumbnailUrl)) return track
        val better = known[track.matchKey()]
        return if (better == null) {
            if (track.thumbnailUrl.isBlank()) track else track.copy(thumbnailUrl = "")
        } else {
            track.copy(thumbnailUrl = better)
        }
    }

    private fun withKnownArtwork(tracks: List<Track>): List<Track> {
        if (tracks.none { it.thumbnailUrl.isBlank() }) return tracks
        val known = knownArtworkByKey()
        if (known.isEmpty()) return tracks
        return tracks.map { fillArt(it, known) }
    }

    private fun refreshCurrentTrackArtwork() {
        val current = _uiState.value.currentTrack ?: return
        if (!needsArtUpgrade(current.thumbnailUrl)) return
        val key = current.matchKey()
        val candidates = (
            _uiState.value.playlists.flatMap { it.tracks } +
                _uiState.value.favorites +
                _uiState.value.localTracks
            ).filter { it.matchKey() == key && it.thumbnailUrl.isNotBlank() }
        val fresh = candidates.firstOrNull { !needsArtUpgrade(it.thumbnailUrl) }
            ?: candidates.firstOrNull { current.thumbnailUrl.isBlank() }
            ?: return
        if (fresh.thumbnailUrl == current.thumbnailUrl) return
        _uiState.value = _uiState.value.copy(
            currentTrack = current.copy(thumbnailUrl = fresh.thumbnailUrl)
        )
    }

    private fun needsArtUpgrade(thumbnailUrl: String): Boolean {
        if (thumbnailUrl.isBlank()) return true
        return thumbnailUrl.startsWith("http", ignoreCase = true) && "/art/" !in thumbnailUrl
    }

    fun onRetagLocalDownloads() {
        val locals = _uiState.value.localTracks
        if (locals.isEmpty()) {
            enqueueTransfer {
                beginTransfer("Fetching music info\u2026")
                finishTransfer("No downloaded files in this folder to tag.")
            }
            return
        }
        enqueueTransfer {
            beginTransfer("Fetching music info…")
            val knownArt = knownArtworkByKey()
            val (ok, failed, skipped) = runParallelTransfer(locals, "Tagging", concurrency = 2) { track ->

                val info = repository.lookupTrackInfo(track.artist, track.title)
                val newTitle = info?.title?.takeIf { it.isNotBlank() } ?: track.title
                val newArtist = info?.artist?.takeIf { it.isNotBlank() } ?: track.artist

                val artUrl = info?.artworkUrl?.takeIf { it.isNotBlank() }
                    ?: knownArt[track.matchKey()]
                    ?: return@runParallelTransfer TransferOutcome.SKIPPED

                if (fileDownloader.saveLocalArtwork(track.streamUrl, newTitle, newArtist, artUrl)) {
                    TransferOutcome.OK
                } else {
                    TransferOutcome.FAILED
                }
            }
            finishTransfer(
                "Updated $ok download(s)" +
                    (if (skipped > 0) ", $skipped had no match" else "") +
                    (if (failed > 0) ", $failed failed." else ".")
            )
            refreshLocalTracks()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        runSearch(query)
    }

    fun onSearchCommitted(query: String) {
        val clean = query.trim()
        if (clean.isBlank() || repository.isLink(clean)) return
        val updated = (listOf(clean) + _uiState.value.searchHistory.filterNot {
            it.equals(clean, ignoreCase = true)
        }).take(10)
        settingsRepository.searchHistoryRaw = updated.joinToString("\n")
        _uiState.value = _uiState.value.copy(searchHistory = updated)
    }

    private val IDENTIFY_SECONDS = 9

    private val IDENTIFY_SECONDS_LONG = 20

    fun onStartIdentify(sourceId: Int, longListen: Boolean = false) {
        val capture = audioCapture ?: return
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                identifyPhase = IdentifyPhase.LISTENING,
                identifyResult = null,
                identifyError = null,
            )
            val seconds = if (longListen) IDENTIFY_SECONDS_LONG else IDENTIFY_SECONDS
            val pcm = capture.record(sourceId, seconds)
            if (pcm == null) {
                _uiState.value = _uiState.value.copy(
                    identifyPhase = IdentifyPhase.ERROR,
                    identifyError = "Couldn't record audio. Check the microphone permission and that the selected device is active.",
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(identifyPhase = IdentifyPhase.MATCHING)
            try {
                val song = repository.recognizeSong(pcm)
                _uiState.value = if (song == null) {
                    _uiState.value.copy(identifyPhase = IdentifyPhase.NO_MATCH)
                } else {
                    _uiState.value.copy(identifyPhase = IdentifyPhase.RESULT, identifyResult = song)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    identifyPhase = IdentifyPhase.ERROR,
                    identifyError = "Couldn't reach the recognition service. Check your connection and try again.",
                )
            }
        }
    }

    fun onDismissIdentify() {
        identifyJob?.cancel()
        _uiState.value = _uiState.value.copy(
            identifyPhase = IdentifyPhase.IDLE,
            identifyResult = null,
            identifyError = null,
        )
    }

    fun onSearchIdentified() {
        val song = _uiState.value.identifyResult ?: return
        onDismissIdentify()
        onSearchQueryChange(listOf(song.artist, song.title).filter { it.isNotBlank() }.joinToString(" "))
    }

    fun onRemoveSearchHistory(query: String) {
        val updated = _uiState.value.searchHistory.filterNot { it == query }
        settingsRepository.searchHistoryRaw = updated.joinToString("\n")
        _uiState.value = _uiState.value.copy(searchHistory = updated)
    }

    fun onClearSearchHistory() {
        settingsRepository.searchHistoryRaw = ""
        _uiState.value = _uiState.value.copy(searchHistory = emptyList())
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSearching = true, searchError = null)
            try {
                if (repository.isLink(query)) {
                    val track = repository.openLink(query)
                    _uiState.value = _uiState.value.copy(
                        searchResults = listOfNotNull(track),
                        isSearching = false,
                        searchError = if (track == null)
                            "Couldn't resolve that link. Check the URL or try again." else null
                    )
                    return@launch
                }
                val results = repository.search(query)
                _uiState.value = _uiState.value.copy(
                    searchResults = results.distinctById(),
                    isSearching = false
                )
            } catch (e: SearchUnavailableException) {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchError = e.message
                )
            } catch (e: Exception) {
                val detail = e.message ?: e.toString()
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchError = "${e::class.simpleName}: $detail"
                )
            }
        }
    }

    fun onTrackClicked(
        track: Track,
        queue: List<Track> = listOf(track),
        forceReload: Boolean = false,

        preserveOrder: Boolean = false
    ) {
        val skipOnFailure = advancingOnItsOwn
        pendingResumeSeekMs = 0L
        _uiState.value = _uiState.value.copy(positionMs = 0L, durationMs = 0L)
        advancingOnItsOwn = false

        fun resolveQueue(): Triple<List<Track>, Int, List<Track>> {
            if (preserveOrder) {
                val cur = _uiState.value.queue.ifEmpty { queue }
                val idx = cur.indexOfFirst { it.id == track.id }
                    .let { if (it >= 0) it else cur.indexOfFirst { q -> songMatchKey(q) == songMatchKey(track) } }
                return Triple(cur, idx.coerceAtLeast(0), _uiState.value.sourceQueue.ifEmpty { cur })
            }
            val (bq, bidx) = buildQueue(track, queue)
            return Triple(bq, bidx, queue)
        }

        val current = _uiState.value.currentTrack
        if (!forceReload && current != null && songMatchKey(current) == songMatchKey(track)) {
            val (q, idx, src) = resolveQueue()
            _uiState.value = _uiState.value.copy(
                sourceQueue = src,
                queue = q,
                queueIndex = idx
            )
            preloadNext()
            return
        }
        recordPlay(track)
        viewModelScope.launch {
            val local = preferLocalCopy(track)
            val playTrack = if (local.source == TrackSource.LOCAL) local else preferServerCopy(track)
            originalForCurrent = track
            fallbackTried = false
            streamRetried = false
            pushHistory(playTrack)
            val (q, idx, src) = resolveQueue()
            _uiState.value = _uiState.value.copy(
                searchError = null,
                currentTrack = playTrack,
                sourceQueue = src,
                queue = q,
                queueIndex = idx
            )
            publishHasPrevious()
            try {
                val (played, url) = resolveWithServerFallback(playTrack)
                if (played !== playTrack) _uiState.value = _uiState.value.copy(currentTrack = played)
                audioPlayer.play(url, played.title, played.artist, played.thumbnailUrl)

                preloadNext()
                saveSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SearchUnavailableException) {
                _uiState.value = _uiState.value.copy(searchError = e.message)
                if (skipOnFailure) skipFailedTrack(playTrack, e.message)
            } catch (e: Exception) {
                val detail = e.message ?: e.toString()
                _uiState.value = _uiState.value.copy(
                    searchError = "${e::class.simpleName}: $detail"
                )
                if (skipOnFailure) skipFailedTrack(playTrack, detail)
            }
        }
    }

    private var originalForCurrent: Track? = null
    private var fallbackTried = false

    private val failedLocalIds = mutableSetOf<String>()

    private fun onPlaybackError() {
        val cur = _uiState.value.currentTrack ?: return
        if (cur.source != TrackSource.LOCAL) {
            retryOrSkipStream(cur)
            return
        }
        val orig = originalForCurrent ?: return
        if (fallbackTried) return
        fallbackTried = true

        failedLocalIds.add(cur.id)
        viewModelScope.launch {
            val alt = preferServerCopy(orig)
            runCatching {
                val (played, url) = resolveWithServerFallback(alt)
                _uiState.value = _uiState.value.copy(currentTrack = played)
                audioPlayer.play(url, played.title, played.artist, played.thumbnailUrl)
            }
        }
    }

    private var streamRetried = false
    private var failedInARow = 0
    private var advancingOnItsOwn = false

    private fun retryOrSkipStream(cur: Track) {
        if (streamRetried) {
            skipFailedTrack(cur, null)
            return
        }
        streamRetried = true
        val at = _uiState.value.positionMs
        println("[MainViewModel] playback error on \"${cur.title}\" at ${at}ms, asking its source again")
        viewModelScope.launch {
            try {
                val (played, url) = resolveWithServerFallback(cur)
                if (played !== cur) _uiState.value = _uiState.value.copy(currentTrack = played)

                if (at > 0) pendingResumeSeekMs = at
                audioPlayer.play(url, played.title, played.artist, played.thumbnailUrl)
                preloadNext()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                skipFailedTrack(cur, e.message ?: e.toString())
            }
        }
    }

    private fun skipFailedTrack(track: Track, reason: String?) {
        pendingResumeSeekMs = 0L
        failedInARow++
        println("[MainViewModel] \"${track.title}\" won't play ($reason); failures in a row: $failedInARow")
        if (computeNext() == null || failedInARow >= MAX_FAILED_IN_A_ROW) {
            failedInARow = 0
            _uiState.value = _uiState.value.copy(
                searchError = "Playback stopped: \"${track.title}\" wouldn't play" +
                    (reason?.let { ". $it" } ?: ".")
            )
            return
        }
        advance(auto = true)
        _uiState.value = _uiState.value.copy(searchError = "Skipped \"${track.title}\": it wouldn't play.")
    }

    fun onPlayClicked() {
        val cur = _uiState.value.currentTrack ?: return
        if (!needsRestore) {
            audioPlayer.resume()
            return
        }
        needsRestore = false
        viewModelScope.launch {
            val localAt = runCatching {
                sessionJson.decodeFromString<SessionSnapshot>(
                    settingsRepository.lastSessionJson.ifBlank { "{}" }
                ).updatedAt
            }.getOrDefault(0L)
            runCatching { pullRemoteSession(localAt) }

            val resumeTrack = _uiState.value.currentTrack ?: return@launch

            val local = preferLocalCopy(resumeTrack)
            val playTrack = if (local.source == TrackSource.LOCAL) local
                else preferServerCopy(resumeTrack)
            _uiState.value = _uiState.value.copy(currentTrack = playTrack)
            try {
                val (played, url) = resolveWithServerFallback(playTrack)
                if (played !== playTrack) _uiState.value = _uiState.value.copy(currentTrack = played)
                audioPlayer.play(url, played.title, played.artist, played.thumbnailUrl)

                if (restoredPositionMs > 0) {
                    pendingResumeSeekMs = restoredPositionMs
                    audioPlayer.seekTo(restoredPositionMs)
                }
                preloadNext()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    searchError = e.message ?: e.toString()
                )
            }
        }
    }

    fun onToggleShuffle() {
        val on = !_uiState.value.shuffle
        _uiState.value = _uiState.value.copy(shuffle = on)
        val cur = _uiState.value.currentTrack
        val source = _uiState.value.sourceQueue.ifEmpty { _uiState.value.queue }
        if (cur != null && source.isNotEmpty()) {
            val anchor = source.firstOrNull { it.id == cur.id }
                ?: source.firstOrNull { songMatchKey(it) == songMatchKey(cur) }
                ?: source.first()
            val (q, idx) = buildQueue(anchor, source)
            _uiState.value = _uiState.value.copy(queue = q, queueIndex = idx)
        }
        preloadNext()
        saveSession()
    }

    fun onCycleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.value = _uiState.value.copy(repeatMode = next)
        preloadNext()
        saveSession()
    }

    fun onPlayAll(tracks: List<Track>) {
        val start = if (_uiState.value.shuffle) tracks.randomOrNull() else tracks.firstOrNull()
        start?.let { onTrackClicked(it, tracks, forceReload = true) }
    }

    private fun recordPlay(track: Track) {
        val key = songMatchKey(track)
        val current = _uiState.value.currentTrack
        if (current != null && songMatchKey(current) == key) return
        val prev = playStats[key]
        playStats[key] = PlayStat((prev?.count ?: 0) + 1, track, prev?.resetAt ?: 0)
        runCatching { settingsRepository.playStatsJson = statsJson.encodeToString(playStats) }
        publishMostPlayed()
    }

    private fun applyStatRename(old: Track, merged: Track) {
        val oldKey = old.matchKey()
        val newKey = merged.matchKey()
        val stat = playStats.remove(oldKey) ?: playStats.remove(newKey) ?: return
        val existing = playStats[newKey]
        playStats[newKey] =
            if (existing != null && existing.count > stat.count) existing.copy(track = merged)
            else stat.copy(track = merged)
    }

    private fun publishMostPlayed() {
        val seen = HashSet<String>()
        val top = playStats.values
            .filter { it.count > 0 }
            .sortedByDescending { it.count }
            .filter { it.track.id.isBlank() || seen.add(it.track.id) }
            .take(50)

        val known = knownArtworkByKey()
        _uiState.value = _uiState.value.copy(
            mostPlayed = top.map { fillArt(it.track, known) },
            playCounts = playStats.mapValues { (_, v) -> v.count }
        )
    }

    fun onRemoveFromMostPlayed(tracks: List<Track>) {
        val keys = tracks.map { it.matchKey() }.toSet()
        val ids = tracks.map { it.id }.filter { it.isNotBlank() }.toSet()
        val now = nowEpochMillis()
        var changed = false
        for ((key, stat) in playStats.entries.toList()) {
            if (key in keys || stat.track.id in ids) {
                playStats[key] = stat.copy(count = 0, resetAt = now)
                changed = true
            }
        }
        if (!changed) return
        runCatching { settingsRepository.playStatsJson = statsJson.encodeToString(playStats) }
        publishMostPlayed()
        autoPushSync()
    }

    private var sleepJob: Job? = null

    fun onSetSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null) {
            _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = null)
            return
        }
        sleepJob = viewModelScope.launch {
            var remaining = minutes * 60_000L
            _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
            }
            audioPlayer.pause()
            _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = null)
        }
    }

    fun onToggleSleepAtEndOfTrack() {
        _uiState.value = _uiState.value.copy(
            sleepAtEndOfTrack = !_uiState.value.sleepAtEndOfTrack
        )

        preloadNext()
    }

    private var pendingNext: Track? = null
    private var preloadJob: Job? = null

    private fun onSongEnded() {
        if (_uiState.value.sleepAtEndOfTrack) {
            _uiState.value = _uiState.value.copy(sleepAtEndOfTrack = false)
            audioPlayer.pause()
            return
        }
        if (_uiState.value.repeatMode == RepeatMode.ONE) {
            _uiState.value.currentTrack?.let {
                onTrackClicked(it, _uiState.value.queue, forceReload = true, preserveOrder = true)
            }
            return
        }
        advance(auto = true)
    }

    fun onNext() = advance(auto = false)

    private fun computeNext(): Track? {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return null
        if (state.repeatMode == RepeatMode.ONE) return queue.getOrNull(state.queueIndex)
        return when {
            state.queueIndex + 1 < queue.size -> queue[state.queueIndex + 1]
            state.repeatMode == RepeatMode.ALL -> queue.firstOrNull()
            else -> null
        }
    }

    private fun buildQueue(track: Track, source: List<Track>): Pair<List<Track>, Int> {
        if (!_uiState.value.shuffle || source.size <= 1) {
            val idx = source.indexOfFirst { it.id == track.id }
                .let { if (it >= 0) it else source.indexOfFirst { s -> songMatchKey(s) == songMatchKey(track) } }
            return source to idx.coerceAtLeast(0)
        }
        val rest = source.filter { it.id != track.id }.shuffled()
        return (listOf(track) + rest) to 0
    }

    private fun advance(auto: Boolean) {
        val next = computeNext() ?: return
        advancingOnItsOwn = auto
        onTrackClicked(next, _uiState.value.queue, forceReload = true, preserveOrder = true)
    }

    private fun preloadNext() {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch {
            val n = if (_uiState.value.sleepAtEndOfTrack) null else computeNext()
            pendingNext = n
            if (n == null) {
                audioPlayer.clearNext()
                return@launch
            }
            try {
                val local = preferLocalCopy(n)
                val playN = if (local.source == TrackSource.LOCAL) local else preferServerCopy(n)
                val url = repository.resolveStreamUrl(playN)
                audioPlayer.enqueueNext(url, playN.title, playN.artist, playN.thumbnailUrl)
            } catch (e: Exception) {
                audioPlayer.clearNext()
            }
        }
    }

    private fun onAutoAdvanced() {
        val n = pendingNext ?: return
        recordPlay(n)
        val queue = _uiState.value.queue
        val idx = queue.indexOfFirst { it.id == n.id }
            .let { if (it >= 0) it else queue.indexOfFirst { q -> songMatchKey(q) == songMatchKey(n) } }
        pushHistory(n)
        streamRetried = false
        _uiState.value = _uiState.value.copy(
            currentTrack = preferLocalCopy(n),
            queueIndex = idx.coerceAtLeast(0)
        )
        publishHasPrevious()
        preloadNext()
        saveSession()
    }

    private fun preferLocalCopy(track: Track, allowLooseMatch: Boolean = true): Track {
        if (track.source == TrackSource.LOCAL && isLocalFileReadable(track.streamUrl)) return track

        fun key(t: Track) = songMatchKey(t)
        val wanted = key(track)
        val state = _uiState.value

        fun localMatch(match: (Track) -> Boolean): Track? =
            state.localTracks.firstOrNull { match(it) && it.id !in failedLocalIds }

        val onDevice = localMatch { key(it) == wanted }

            ?: if (allowLooseMatch) {
                track.looseKey()?.let { loose -> localMatch { it.looseKey() == loose } }
            } else {
                null
            }
        if (onDevice != null) {
            return if (onDevice.thumbnailUrl.isBlank()) {
                onDevice.copy(thumbnailUrl = track.thumbnailUrl)
            } else {
                onDevice
            }
        }

        val owned = (state.favorites +
            state.playlists.flatMap { it.tracks } +
            state.browseTracks)
            .firstOrNull { key(it) == wanted && it.serverBaseUrl.isNotBlank() }

        return owned ?: track
    }

    private fun preferServerCopy(track: Track): Track {
        if (track.serverBaseUrl.isNotBlank()) return track
        val wanted = songMatchKey(track)

        serverCopyFor(wanted)?.let { return it }
        val state = _uiState.value
        val owned = (state.favorites +
            state.playlists.flatMap { it.tracks } +
            state.browseTracks)
            .firstOrNull { songMatchKey(it) == wanted && it.serverBaseUrl.isNotBlank() }
        return owned ?: track
    }

    private fun uploadExtFor(bytes: ByteArray, track: Track): String =
        AudioContainer.extensionOf(bytes)
            ?: track.streamUrl.substringAfterLast('.', "").lowercase()
                .takeIf { it.isNotBlank() && it.length <= 5 }
            ?: "mp3"

    private fun historyWindow(): List<Track> =
        playHistory.toList().takeLast(HISTORY_WINDOW)

    private fun serverUploadName(artist: String, title: String, ext: String): String {
        val base = "$artist - $title"
            .replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
            .ifBlank { "track" }
        return "$base.$ext"
    }

    private fun preferLocalFile(track: Track): Track {
        if (track.source == TrackSource.LOCAL) return track
        val wanted = songMatchKey(track)
        return _uiState.value.localTracks
            .firstOrNull { songMatchKey(it) == wanted } ?: track
    }

    private var suppressHistoryPush = false

    private fun pushHistory(newTrack: Track) {
        val prev = _uiState.value.currentTrack
        if (!suppressHistoryPush && prev != null && prev.id != newTrack.id) {
            playHistory.addLast(prev)
            while (playHistory.size > 100) playHistory.removeFirst()
        }
        suppressHistoryPush = false
        publishHasPrevious()
    }

    private fun publishHasPrevious() {
        val can = playHistory.isNotEmpty() || _uiState.value.queueIndex > 0
        if (_uiState.value.hasPrevious != can) {
            _uiState.value = _uiState.value.copy(hasPrevious = can)
        }
    }

    fun onPrevious() {
        if (_uiState.value.positionMs > 3_000L) {
            audioPlayer.seekTo(0)
            return
        }
        onPreviousTrack()
    }

    fun onPreviousTrack() {
        val state = _uiState.value
        val lastPlayed = playHistory.removeLastOrNull()
        if (lastPlayed != null) {
            suppressHistoryPush = true
            onTrackClicked(lastPlayed, state.queue, forceReload = true, preserveOrder = true)
            return
        }
        val prev = state.queue.getOrNull(state.queueIndex - 1) ?: return
        onTrackClicked(prev, state.queue, forceReload = true, preserveOrder = true)
    }

    private fun trackKey(t: Track) = songMatchKey(t)

    private fun songMatchKey(t: Track): String = t.matchKey()

    private fun canDownloadToDevice(track: Track): Boolean =
        track.source != TrackSource.LOCAL &&
            track.downloadable

    private fun isAlreadyOnDevice(track: Track): Boolean {
        if (track.source == TrackSource.LOCAL) return true
        val k = trackKey(track)
        return _uiState.value.localTracks.any { trackKey(it) == k }
    }

    fun onDownloadClicked(track: Track) {
        if (!canDownloadToDevice(track)) return
        if (isAlreadyOnDevice(track)) {
            _uiState.value = _uiState.value.copy(downloadStatus = "Already on this device.")
            beginTransfer("Already on this device")
            finishTransfer("Already on this device.")
            return
        }
        enqueueTransfer {
            beginTransfer("Saving…")
            val status = try {
                val source = preferServerCopy(track)
                val result = saveTrackToDevice(source, subfolder = "") { p -> updateTransferProgress(p) }
                when (result) {
                    is DownloadResult.Success -> "Saved to ${result.location}"
                    is DownloadResult.Error -> "Download failed: ${result.message}"
                }
            } catch (e: Exception) {
                "Download failed: ${e.message ?: e.toString()}"
            }
            finishTransfer(status)
        }
    }

    private var transferJob: Job? = null

    private val transferQueue = ArrayDeque<suspend () -> Unit>()

    private val transferQueueLock = Mutex()

    private fun enqueueTransfer(block: suspend () -> Unit) {
        viewModelScope.launch {
            transferQueueLock.withLock { transferQueue.addLast(block) }
            publishQueueDepth()
            if (transferJob?.isActive == true) return@launch
            transferJob = viewModelScope.launch {
                while (true) {
                    val next = transferQueueLock.withLock { transferQueue.removeFirstOrNull() }
                        ?: break
                    publishQueueDepth()
                    next()

                    runCatching { scanLocalTracks(showLoading = false) }
                }
                publishQueueDepth()
            }
        }
    }

    private fun publishQueueDepth() {
        _uiState.value = _uiState.value.copy(transferQueueDepth = transferQueue.size)
    }

    private var lastTransferError: String? = null

    private fun beginTransfer(label: String) {
        _uiState.value = _uiState.value.copy(
            downloadStatus = label, transferProgress = -1f, transferLabel = label
        )

        TransferController.reset()
        TransferController.onCancel = { transferJob?.cancel() }

        TransferNotifier.start(label)
        TransferNotifier.update(-1f, label)
    }

    fun cancelTransfer() {
        TransferController.requestCancel()
        transferJob?.cancel()
        transferJob = null

        transferQueue.clear()
        _uiState.value = _uiState.value.copy(
            transferProgress = null,
            transferLabel = null,
            downloadStatus = "Cancelled",
            transferQueueDepth = 0
        )
        TransferNotifier.cancel()
    }

    private fun updateTransferProgress(p: Float) {
        _uiState.value = _uiState.value.copy(transferProgress = p)
        TransferNotifier.update(p, _uiState.value.transferLabel ?: "Transferring…")
    }

    private fun finishTransfer(status: String) {
        if (TransferController.cancelled) return
        val ok = !status.startsWith("✗") && !status.startsWith("Download failed")
        _uiState.value = _uiState.value.copy(
            downloadStatus = status,
            transferProgress = 1f,
            transferLabel = if (ok) "Done" else status
        )
        TransferNotifier.finish(status, ok)

        viewModelScope.launch {
            delay(2500)
            _uiState.value = _uiState.value.copy(transferProgress = null, transferLabel = null)
        }
    }

    private suspend fun resolveDownloadUrl(track: Track): String {
        repository.resolveExtensionDownload(track)?.let { return it.url }
        if (track.source != TrackSource.LOCAL && track.serverBaseUrl.isBlank()) {
            throw SearchUnavailableException("This track's source doesn't allow saving it.", null)
        }
        return repository.resolveStreamUrl(track)
    }

    private suspend fun saveTrackToDevice(
        track: Track,
        subfolder: String,
        onProgress: (Float) -> Unit
    ): DownloadResult {
        val native = repository.resolveExtensionDownload(track)
        if (native != null) {
            val artUrl = repository.lookupArtwork(track.artist, track.title)
                ?: track.thumbnailUrl
            return fileDownloader.downloadRaw(
                native.url, track.title, track.artist,
                ext = native.ext, mime = native.mime, subfolder = subfolder,
                artworkUrl = artUrl, onProgress = onProgress
            )
        }
        val url = resolveDownloadUrl(track)
        return fileDownloader.downloadAudio(
            url, track.title, track.artist, track.thumbnailUrl,
            subfolder = subfolder, onProgress = onProgress
        )
    }

    fun onDownloadTrackToServer(track: Track, serverBaseUrl: String) {
        onUploadPlaylistToServer(listOf(track), serverBaseUrl)
    }

    fun onDownloadPlaylistToDevice(tracks: List<Track>, playlistName: String = "") {
        val savable = tracks.filter { canDownloadToDevice(it) }
        if (savable.isEmpty()) {
            _uiState.value = _uiState.value.copy(downloadStatus = "Nothing here can be saved.")
            return
        }
        enqueueTransfer {
            beginTransfer("Saving to device…")
            val (ok, failed, skipped) = runParallelTransfer(savable, "Saving to device") { track ->
                if (isAlreadyOnDevice(track)) {
                    TransferOutcome.SKIPPED
                } else when (saveTrackToDevice(preferServerCopy(track), subfolder = playlistName) {}) {
                    is DownloadResult.Success -> TransferOutcome.OK
                    is DownloadResult.Error -> TransferOutcome.FAILED
                }
            }
            finishTransfer(
                "Saved $ok to device" +
                    (if (skipped > 0) ", $skipped already there" else "") +
                    (if (failed > 0) ", $failed failed." else ".")
            )
        }
    }

    private suspend fun <T> runParallelTransfer(
        items: List<T>,
        labelPrefix: String,
        concurrency: Int = 3,
        doOne: suspend (T) -> TransferOutcome
    ): Triple<Int, Int, Int> {
        val total = items.size
        val gate = Semaphore(concurrency)
        val mutex = Mutex()
        var done = 0
        var ok = 0
        var failed = 0
        var skipped = 0
        lastTransferError = null
        coroutineScope {
            items.map { item ->
                async {
                    gate.withPermit {
                        ensureActive()
                        val outcome = try {
                            doOne(item)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            println("Transfer fail: ${e::class.simpleName}: ${e.message}")
                            lastTransferError = "${e::class.simpleName}: ${e.message}"
                            TransferOutcome.FAILED
                        }
                        mutex.withLock {
                            done++
                            when (outcome) {
                                TransferOutcome.OK -> ok++
                                TransferOutcome.FAILED -> failed++
                                TransferOutcome.SKIPPED -> skipped++
                            }
                            _uiState.value = _uiState.value.copy(
                                transferLabel = "$labelPrefix $done/$total",
                                downloadStatus = "$labelPrefix $done/$total"
                            )
                            updateTransferProgress(done.toFloat() / total)
                        }
                    }
                }
            }.awaitAll()
        }
        return Triple(ok, failed, skipped)
    }

    fun onUploadPlaylistToServer(
        tracks: List<Track>,
        serverBaseUrl: String,
        playlistName: String = "",
        preferLocal: Boolean = true
    ) {
        val targetFolder = playlistName.ifBlank { settingsRepository.defaultServerDir }
        if (tracks.isEmpty()) {
            _uiState.value = _uiState.value.copy(downloadStatus = "Nothing here to send.")
            return
        }
        enqueueTransfer {
            beginTransfer("Uploading…")

            var listingOk = true
            val claimedKeys = try {
                repository.browsePlaylistTracks(serverBaseUrl, "__all__")
                    .map { it.matchKey() }.toMutableSet()
            } catch (e: Exception) {
                listingOk = false
                mutableSetOf()
            }
            val claimMutex = Mutex()
            val failures = mutableListOf<String>()
            val (ok, _, skipped) = runParallelTransfer(tracks, "Uploading") { original ->

                val track = if (preferLocal) preferLocalFile(original) else original

                val fresh = claimMutex.withLock { claimedKeys.add(songMatchKey(track)) }
                if (!fresh ||
                    (!listingOk && repository.existsOnServer(serverBaseUrl, track.title, track.artist))
                ) {
                    TransferOutcome.SKIPPED
                } else {
                    var failure = uploadOneToServer(track, serverBaseUrl, targetFolder)
                    if (failure?.retryable == true) {
                        delay(UPLOAD_RETRY_DELAY_MS)
                        failure = uploadOneToServer(track, serverBaseUrl, targetFolder)
                    }
                    if (failure == null) {
                        markOnServer(original, serverBaseUrl)
                        TransferOutcome.OK
                    } else {
                        claimMutex.withLock { failures += "\"${original.title}\" (${failure.reason})" }
                        TransferOutcome.FAILED
                    }
                }
            }
            finishTransfer(uploadSummary(ok, skipped, failures))

            if (ok > 0) refreshServerLibrary()
        }
    }

    private class UploadFailure(val reason: String, val retryable: Boolean)

    private suspend fun uploadOneToServer(track: Track, serverBaseUrl: String, folder: String): UploadFailure? {
        return try {
            if (track.source == TrackSource.LOCAL) {
                val bytes = readLocalTrackBytes(track.streamUrl)
                    ?: return UploadFailure("the file on this phone couldn't be read", retryable = false)
                repository.uploadFileToServer(
                    serverBaseUrl,
                    serverUploadName(track.artist, track.title, uploadExtFor(bytes, track)),
                    bytes,
                    folder,
                    title = track.title,
                    artist = track.artist,
                    artUrl = track.thumbnailUrl
                )
            } else {
                val native = repository.resolveExtensionDownload(track)
                    ?: return UploadFailure("no switched-on source can save it", retryable = false)
                val raw = repository.downloadAudioBytes(native.url)

                val artUrl = repository.lookupArtwork(track.artist, track.title) ?: track.thumbnailUrl
                val bytes = fileDownloader.tagAudio(raw, track.title, track.artist, native.ext, artUrl)
                repository.uploadFileToServer(
                    serverBaseUrl,
                    serverUploadName(track.artist, track.title, native.ext),
                    bytes,
                    folder,
                    title = track.title,
                    artist = track.artist,
                    artUrl = artUrl
                )
            }
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UploadFailure(failureReason(e), retryable = true)
        }
    }

    private fun failureReason(e: Exception): String =
        (e.message ?: e::class.simpleName ?: "error")
            .removePrefix("Upload failed: ")
            .lineSequence().first()
            .take(80)

    private fun uploadSummary(ok: Int, skipped: Int, failures: List<String>): String = buildString {
        append("Uploaded $ok to server")
        if (skipped > 0) append(", $skipped already there")
        if (failures.isEmpty()) {
            append(".")
        } else {
            append(", ${failures.size} failed: ")
            append(failures.take(3).joinToString("; "))
            if (failures.size > 3) append("; and ${failures.size - 3} more")
            append(".")
        }
    }

    fun onDeleteLocalTrack(track: Track) {
        viewModelScope.launch {
            beginTransfer("Deleting…")
            val ok = try { deleteLocalTrack(track.streamUrl) } catch (e: Exception) { false }
            if (ok) {
                _uiState.value = _uiState.value.copy(
                    localTracks = _uiState.value.localTracks.filterNot { it.id == track.id }
                )
            }
            finishTransfer(if (ok) "Deleted from device." else "✗ Couldn't delete from device.")
        }
    }

    fun onDeleteLocalCopy(track: Track) {
        val local = preferLocalCopy(track, allowLooseMatch = false)
        if (local.source == TrackSource.LOCAL) onDeleteLocalTrack(local)
    }

    fun onDeleteFromServer(track: Track) {
        val target = if (track.serverBaseUrl.isBlank()) preferServerCopy(track) else track
        if (target.serverBaseUrl.isBlank()) return
        viewModelScope.launch {
            beginTransfer("Deleting from server…")
            val status = try {
                repository.deleteFromServer(target.serverBaseUrl, target.itemId)

                val gone = setOf(target.id)
                val s = _uiState.value
                _uiState.value = s.copy(
                    browseTracks = s.browseTracks.filterNot { it.id in gone },
                    searchResults = s.searchResults.filterNot { it.id in gone },
                    favorites = s.favorites.filterNot { it.id in gone },
                    playlists = s.playlists.map { pl ->
                        pl.copy(tracks = pl.tracks.filterNot { it.id in gone })
                    }
                )
                refreshServerLibrary()
                "Deleted from server."
            } catch (e: Exception) {
                "✗ ${e.message ?: e}"
            }
            finishTransfer(status)
        }
    }

    fun onDeleteAllLocal() {
        val locals = _uiState.value.localTracks
        if (locals.isEmpty()) return
        enqueueTransfer {
            beginTransfer("Deleting all…")
            var ok = 0
            for (t in locals) {
                if (runCatching { deleteLocalTrack(t.streamUrl) }.getOrDefault(false)) ok++
            }
            refreshLocalTracks()
            finishTransfer("Deleted $ok from device.")
        }
    }

    fun onDeleteLocalTracks(tracks: List<Track>) {
        val locals = tracks.filter { it.source == TrackSource.LOCAL }
        if (locals.isEmpty()) return
        enqueueTransfer {
            beginTransfer("Deleting…")
            var ok = 0
            for (t in locals) {
                if (runCatching { deleteLocalTrack(t.streamUrl) }.getOrDefault(false)) ok++
            }
            refreshLocalTracks()
            finishTransfer("Deleted $ok from device.")
        }
    }

    fun onDeleteLocalFolder(folder: String) {
        onDeleteLocalTracks(_uiState.value.localTracks.filter { it.localFolder == folder })
    }

    fun onDeleteBrowseFolder(baseUrl: String, playlistId: String) {
        enqueueTransfer {
            beginTransfer("Deleting folder from server…")
            val tracks = try {
                repository.browsePlaylistTracks(baseUrl, playlistId)
            } catch (e: Exception) {
                finishTransfer("✗ Couldn't load that folder.")
                return@enqueueTransfer
            }
            val serverTracks = tracks.filter { it.serverBaseUrl.isNotBlank() }
            var ok = 0
            for (t in serverTracks) {
                runCatching { repository.deleteFromServer(t.serverBaseUrl, t.itemId) }.onSuccess { ok++ }
            }
            onBrowseSource(baseUrl)
            finishTransfer("Deleted $ok from server.")
        }
    }

    fun onDeleteAllFromServer(tracks: List<Track>) {
        val serverTracks = tracks.filter { it.serverBaseUrl.isNotBlank() }
        if (serverTracks.isEmpty()) return
        enqueueTransfer {
            beginTransfer("Deleting all from server…")
            var ok = 0
            for (t in serverTracks) {
                runCatching { repository.deleteFromServer(t.serverBaseUrl, t.itemId) }
                    .onSuccess { ok++ }
            }
            val gone = serverTracks.map { it.id }.toSet()
            _uiState.value = _uiState.value.copy(
                browseTracks = _uiState.value.browseTracks.filterNot { it.id in gone }
            )
            finishTransfer("Deleted $ok from server.")
        }
    }

    fun onPickVersion(track: Track) {
        _uiState.value = _uiState.value.copy(
            versionPickerFor = track,
            versionOptions = emptyList(),
            versionLoading = true
        )
        viewModelScope.launch {
            val query = listOf(track.artist, track.title).filter { it.isNotBlank() }.joinToString(" ")

            val results = try {
                repository.search(query, musicOnly = false)
                    .filter { it.itemId != track.itemId }
            } catch (e: Exception) {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(versionOptions = results, versionLoading = false)
        }
    }

    fun onDismissVersionPicker() {
        _uiState.value = _uiState.value.copy(
            versionPickerFor = null, versionOptions = emptyList(), versionLoading = false
        )
    }

    fun onPreviewVersionFromLink(input: String) {
        val clean = input.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val track = runCatching { repository.openLink(clean) }.getOrNull()
            if (track == null) {
                _uiState.value = _uiState.value.copy(
                    searchError = "Couldn't resolve that link. Check the URL and try again."
                )
            } else {
                onPreviewVersion(track)
            }
        }
    }

    fun onChooseVersionFromLink(input: String) {
        val clean = input.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            val track = runCatching { repository.openLink(clean) }.getOrNull()
            if (track == null) {
                _uiState.value = _uiState.value.copy(
                    searchError = "Couldn't resolve that link. Check the URL and try again."
                )
            } else {
                onChooseVersion(track)
            }
        }
    }

    fun onPreviewVersion(track: Track) {
        viewModelScope.launch {
            val playTrack = preferLocalCopy(track)
            _uiState.value = _uiState.value.copy(currentTrack = playTrack, searchError = null)
            try {
                val url = repository.resolveStreamUrl(playTrack)
                audioPlayer.play(url, playTrack.title, playTrack.artist, playTrack.thumbnailUrl)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(searchError = e.message ?: e.toString())
            }
        }
    }

    fun onChooseVersion(chosen: Track) {
        val original = _uiState.value.versionPickerFor ?: return

        val merged = chosen.copy(
            title = original.title,
            artist = original.artist,
            thumbnailUrl = original.thumbnailUrl.ifBlank { chosen.thumbnailUrl }
        )

        libraryRepository.replaceInPlaylists(original.id, merged)

        val s = _uiState.value
        val newQueue = s.queue.map { if (it.id == original.id) merged else it }
        _uiState.value = s.copy(
            queue = newQueue,
            favorites = s.favorites.map { if (it.id == original.id) merged else it },
            browseTracks = s.browseTracks.map { if (it.id == original.id) merged else it },
            searchResults = s.searchResults.map { if (it.id == original.id) merged else it },
            versionPickerFor = null,
            versionOptions = emptyList(),
            versionLoading = false
        )
        refreshLibrary()

        val wasLocal = original.source == TrackSource.LOCAL
        val serverUrl = original.serverBaseUrl
        when {
            wasLocal -> {
                enqueueTransfer {
                    beginTransfer("Replacing download…")
                    val status = try {
                        runCatching { deleteLocalTrack(original.streamUrl) }
                        _uiState.value = _uiState.value.copy(
                            localTracks = _uiState.value.localTracks.filterNot { it.id == original.id }
                        )
                        val r = saveTrackToDevice(merged, subfolder = "") { p -> updateTransferProgress(p) }
                        refreshLocalTracks()
                        when (r) {
                            is DownloadResult.Success -> "Replaced the downloaded version."
                            is DownloadResult.Error -> "✗ ${r.message}"
                        }
                    } catch (e: Exception) {
                        "✗ ${e.message ?: e}"
                    }
                    finishTransfer(status)
                }
            }

            serverUrl.isNotBlank() -> {
                viewModelScope.launch {
                    runCatching { repository.deleteFromServer(serverUrl, original.itemId) }

                    onUploadPlaylistToServer(listOf(merged), serverUrl, preferLocal = false)
                }
            }
        }
    }

    fun onPauseClicked() {
        audioPlayer.pause()
    }

    fun onSeek(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun onBrowseSource(baseUrl: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                browseLoading = true,
                browseError = null,
                browsePlaylists = emptyList(),
                browseTracks = emptyList()
            )

            val result = runCatching { repository.browsePlaylistsOrThrow(baseUrl) }
            val playlists = result.getOrDefault(emptyList())
            _uiState.value = _uiState.value.copy(
                browseLoading = false,
                browsePlaylists = playlists,
                browseError = when {
                    result.isFailure ->
                        "Couldn't reach this server. If it's on Oracle, check the port is " +
                            "open (security list + firewall)."
                    playlists.isEmpty() ->
                        "This source has no folders yet. Upload a track, or use search."
                    else -> null
                }
            )
        }
    }

    private var lastBrowseBaseUrl: String = ""
    private var lastBrowsePlaylistId: String = ""

    fun onSearchBrowseSource(baseUrl: String, query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        lastBrowseBaseUrl = baseUrl

        lastBrowsePlaylistId = ""
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(browseLoading = true, browseError = null)
            val tracks = try {
                repository.searchLibrary(baseUrl, clean)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    browseLoading = false,
                    browseError = e.message ?: "Couldn't search this server."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                browseLoading = false,
                browseTracks = tracks.distinctById()
            )
            enrichBrowseArtwork()
        }
    }

    fun onOpenBrowsePlaylist(baseUrl: String, playlistId: String) {
        lastBrowseBaseUrl = baseUrl
        lastBrowsePlaylistId = playlistId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(browseLoading = true, browseError = null)
            val tracks = try {
                repository.browsePlaylistTracks(baseUrl, playlistId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    browseLoading = false,
                    browseError = e.message ?: "Couldn't load this folder."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                browseLoading = false,
                browseTracks = tracks.distinctById()
            )
            enrichBrowseArtwork()
        }
    }

    fun onPlayBrowsePlaylist(baseUrl: String, playlistId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(browseError = null)
            val tracks = try {
                repository.browsePlaylistTracks(baseUrl, playlistId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    browseError = e.message ?: "Couldn't load this folder."
                )
                return@launch
            }
            if (tracks.isEmpty()) return@launch
            onPlayAll(tracks)
        }
    }

    private fun enrichBrowseArtwork() {
        val blanks = _uiState.value.browseTracks.filter { it.thumbnailUrl.isBlank() }
        if (blanks.isEmpty()) return
        viewModelScope.launch {
            blanks.forEach { track ->
                val query = listOf(track.artist, track.title)
                    .filter { it.isNotBlank() }.joinToString(" ")
                if (query.isBlank()) return@forEach
                val art = try {
                    repository.search(query).firstOrNull { it.thumbnailUrl.isNotBlank() }?.thumbnailUrl
                } catch (e: Exception) {
                    null
                } ?: return@forEach
                _uiState.value = _uiState.value.copy(
                    browseTracks = _uiState.value.browseTracks.map {
                        if (it.id == track.id && it.thumbnailUrl.isBlank()) it.copy(thumbnailUrl = art) else it
                    }
                )
            }
        }
    }

    fun onRefreshBrowse() {
        val baseUrl = lastBrowseBaseUrl
        val playlistId = lastBrowsePlaylistId
        if (baseUrl.isBlank() || playlistId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(browseLoading = true, browseError = null)
            repository.rescanServer(baseUrl)
            val tracks = try {
                repository.browsePlaylistTracks(baseUrl, playlistId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    browseLoading = false,
                    browseError = e.message ?: "Couldn't refresh this folder."
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                browseLoading = false,
                browseTracks = tracks.distinctById()
            )
            enrichBrowseArtwork()
        }
    }

    private fun refreshLibrary() {
        val storedPlaylists = libraryRepository.playlists()
        val storedFavorites = libraryRepository.favorites()

        val known = artworkIndex(
            storedPlaylists.flatMap { it.tracks } + storedFavorites + _uiState.value.localTracks
        )
        _uiState.value = _uiState.value.copy(
            playlists = storedPlaylists.map { p -> p.copy(tracks = p.tracks.map { fillArt(it, known) }) },
            favorites = storedFavorites.map { fillArt(it, known) },
            favoriteTrackIds = storedFavorites.map { it.id }.toSet()
        )

        autoPushSync()
    }

    fun refreshServerLibrary() {
        val bases = serverBasesInOrder()
        if (bases.isEmpty()) {
            serverCopies = emptyMap()
            serverLibraryTracks = emptyMap()
            _uiState.value = _uiState.value.copy(serverLibraryKeys = emptySet())
            return
        }
        viewModelScope.launch {
            val copies = linkedMapOf<String, MutableList<Track>>()
            val tracks = mutableListOf<Track>()
            var anyAnswered = false
            for (base in bases) {
                val found = try {
                    repository.browsePlaylistTracks(base, "__all__")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    markServerDown(base)
                    continue
                }
                downServers.remove(base)
                anyAnswered = true
                tracks += found
                found.filter { it.serverBaseUrl.isNotBlank() }
                    .forEach { copies.getOrPut(songMatchKey(it)) { mutableListOf() }.add(it) }
            }
            if (!anyAnswered) return@launch

            serverCopies = copies
            serverLibraryTracks = copies.mapValues { it.value.first() }
            val listed = (tracks.map { it.matchKey() } + tracks.mapNotNull { it.looseKey() }).toSet()

            uploadedNotListed.entries.removeAll { (key, at) ->
                key in listed || at.elapsedNow() > UPLOAD_MARK_KEPT
            }
            _uiState.value = _uiState.value.copy(serverLibraryKeys = listed + uploadedNotListed.keys)

            if (uploadedNotListed.isNotEmpty() && !recheckPending) {
                recheckPending = true
                delay(SERVER_RECHECK_MS)
                recheckPending = false
                refreshServerLibrary()
            }
        }
    }

    private val uploadedNotListed = mutableMapOf<String, kotlin.time.TimeMark>()
    private var recheckPending = false

    private fun markOnServer(track: Track, serverBaseUrl: String) {
        if (serverBaseUrl.trimEnd('/') != libraryServerUrl()?.trimEnd('/')) return
        val now = kotlin.time.TimeSource.Monotonic.markNow()
        listOfNotNull(track.matchKey(), track.looseKey()).forEach { uploadedNotListed[it] = now }
        _uiState.value = _uiState.value.copy(
            serverLibraryKeys = _uiState.value.serverLibraryKeys + uploadedNotListed.keys
        )
    }

    fun onToggleFavorite(track: Track) {
        libraryRepository.toggleFavorite(track)
        refreshLibrary()
    }

    private fun buildSyncState(): PlaylistSyncState = PlaylistSyncState(
        playlists = libraryRepository.playlists().map { p ->
            p.copy(tracks = p.tracks.map { it.copy(thumbnailUrl = portableArt(it.thumbnailUrl)) })
        },
        favorites = libraryRepository.favorites().map {
            it.copy(thumbnailUrl = portableArt(it.thumbnailUrl))
        },
        updatedAt = nowEpochMillis(),
        device = "phone",

        playStatsJson = runCatching {
            statsJson.encodeToString(
                playStats.mapValues { (_, v) ->
                    v.copy(track = v.track.copy(thumbnailUrl = portableArt(v.track.thumbnailUrl)))
                }.toMutableMap()
            )
        }.getOrDefault(""),

        themes = if (settingsRepository.syncThemeEnabled)
            themeRegistry.all().filterNot { it.builtIn } else emptyList(),
        sources = pluginManager.all().filterNot { it.builtIn },
        selectedThemeId = if (settingsRepository.syncThemeEnabled)
            themeRegistry.selectedId() else "",
        settingsOrderCsv = settingsRepository.settingsOrderCsv
    )

    private fun applyRemoteSync(remote: PlaylistSyncState) {
        libraryRepository.mergeSynced(
            remote.playlists.map { p ->
                p.copy(tracks = p.tracks.map { it.copy(thumbnailUrl = portableArt(it.thumbnailUrl)) })
            },
            remote.favorites.map { it.copy(thumbnailUrl = portableArt(it.thumbnailUrl)) }
        )
        if (remote.playStatsJson.isNotBlank()) {
            runCatching {
                statsJson.decodeFromString<MutableMap<String, PlayStat>>(remote.playStatsJson)
            }.getOrNull()?.forEach { (key, stat) ->
                val cur = playStats[key]
                if (cur == null || stat.resetAt > cur.resetAt ||
                    (stat.resetAt == cur.resetAt && stat.count > cur.count)
                ) {
                    playStats[key] = stat.copy(
                        track = stat.track.copy(
                            thumbnailUrl = portableArt(stat.track.thumbnailUrl)
                        )
                    )
                }
            }
            runCatching { settingsRepository.playStatsJson = statsJson.encodeToString(playStats) }
            publishMostPlayed()
        }

        if (settingsRepository.syncThemeEnabled) {
            val themeIds = themeRegistry.all().map { it.id }.toSet()
            remote.themes.filterNot { it.builtIn || it.id in themeIds }.forEach { t ->
                themeRegistry.install(t.name, t.primary, t.secondary, t.tertiary, t.background, t.dark)
            }
        }
        val sourceIds = pluginManager.all().map { it.id }.toSet()
        remote.sources.filterNot { it.builtIn || it.id in sourceIds }.forEach { s ->
            pluginManager.upsert(s.id, s.name, s.baseUrl)
        }

        if (settingsRepository.syncThemeEnabled &&
            remote.selectedThemeId.isNotBlank() &&
            themeRegistry.all().any { it.id == remote.selectedThemeId }
        ) {
            themeRegistry.select(remote.selectedThemeId)
        }

        if (remote.settingsOrderCsv.isNotBlank()) {
            settingsRepository.settingsOrderCsv = remote.settingsOrderCsv
        }
        refreshLibrary()
        _uiState.value = _uiState.value.copy(
            themes = themeRegistry.all(),
            selectedThemeId = themeRegistry.selectedId(),
            sources = pluginManager.all(),
            settingsSectionOrder = settingsRepository.settingsOrderCsv
                .split(",").map { it.trim() }.filter { it.isNotBlank() }
        )
    }

    fun onSyncPlaylistsToServer() {
        val base = libraryServerUrl() ?: return
        viewModelScope.launch {
            beginTransfer("Syncing to server…")
            val ok = repository.putPlaylistSync(base, buildSyncState())
            finishTransfer(
                if (ok) "Library synced to server."
                else "Couldn't reach the server: sync failed."
            )
        }
    }

    fun onFetchPlaylistsFromServer() {
        val base = libraryServerUrl() ?: return
        viewModelScope.launch {
            beginTransfer("Fetching from server…")
            val remote = repository.getPlaylistSync(base)
            if (remote == null) {
                finishTransfer("No sync on the server yet: sync from a device first.")
                return@launch
            }
            applyRemoteSync(remote)
            finishTransfer(
                "Fetched ${remote.playlists.size} playlist(s) and " +
                    "${remote.favorites.size} liked song(s) from the server."
            )
        }
    }

    fun onToggleAutoSync() {
        val on = !settingsRepository.syncAutoEnabled
        settingsRepository.syncAutoEnabled = on
        _uiState.value = _uiState.value.copy(autoSyncEnabled = on)
        if (on) {
            val base = libraryServerUrl() ?: return
            viewModelScope.launch {
                repository.getPlaylistSync(base)?.let { applyRemoteSync(it) }
                repository.putPlaylistSync(base, buildSyncState())
            }
        }
    }

    fun onToggleThemeSync() {
        val on = !settingsRepository.syncThemeEnabled
        settingsRepository.syncThemeEnabled = on
        _uiState.value = _uiState.value.copy(themeSyncEnabled = on)
        if (on) autoPushSync()
    }

    fun onTogglePlaybackSync() {
        val on = !settingsRepository.syncPlaybackEnabled
        settingsRepository.syncPlaybackEnabled = on
        _uiState.value = _uiState.value.copy(playbackSyncEnabled = on)
        if (on) saveSession()
    }

    private var autoPushJob: Job? = null

    private fun autoPushSync() {
        if (!settingsRepository.syncAutoEnabled) return
        val base = libraryServerUrl() ?: return
        autoPushJob?.cancel()
        autoPushJob = viewModelScope.launch {
            delay(2_000)
            repository.putPlaylistSync(base, buildSyncState())
        }
    }

    private val updateChecker by lazy {
        UpdateChecker(client = repository.httpClient, repo = "Kevcar98/Spinet-Mobile")
    }

    fun onCheckForUpdates(manual: Boolean = false) {
        val installer = updateInstaller ?: return
        viewModelScope.launch {
            val found = updateChecker.check(installer::wants)
            _uiState.value = _uiState.value.copy(
                availableUpdate = found,
                updateDismissed = false,

                updateDialogOpen = manual && found != null,
                updateError = when {
                    !manual || found != null -> null
                    updateChecker.lastError != null ->
                        "Couldn't check for updates: ${updateChecker.lastError}"
                    else -> "You are on the latest version."
                }
            )
        }
    }

    fun onInstallUpdate() {
        if (_uiState.value.updateBusy) return
        val installer = updateInstaller ?: return
        val update = _uiState.value.availableUpdate ?: return
        val blocked = installer.blockedReason()
        if (blocked != null) {
            _uiState.value = _uiState.value.copy(updateError = blocked)
            installer.resolveBlock()
            return
        }
        _uiState.value = _uiState.value.copy(updateBusy = true, updateError = null)
        viewModelScope.launch {
            val result = runCatching {
                val bytes = repository.downloadBytes(update.downloadUrl)
                val sums = update.checksumsUrl?.let {
                    runCatching { repository.downloadBytes(it).decodeToString() }.getOrNull()
                }
                installer.install(update.downloadName, bytes, sums)
            }.getOrElse { "Download failed: ${it.message ?: it::class.simpleName}" }

            _uiState.value = _uiState.value.copy(
                updateBusy = false,
                updateError = result,

                availableUpdate = if (result == null) null else _uiState.value.availableUpdate,
                updateDismissed = false,
                updateDialogOpen = _uiState.value.updateDialogOpen && result != null
            )
        }
    }

    fun onDismissUpdate() {
        _uiState.value = _uiState.value.copy(
            updateDismissed = true,
            updateDialogOpen = false,
            updateError = null
        )
    }

    fun onShowUpdate() {
        _uiState.value = _uiState.value.copy(updateDismissed = false, updateDialogOpen = true)
    }

    fun onToggleSelect(track: Track) {
        val current = _uiState.value.selectedTrackIds
        val next = if (track.id in current) current - track.id else current + track.id
        _uiState.value = _uiState.value.copy(selectedTrackIds = next)
    }

    fun onSelectAll(tracks: List<Track>) {
        _uiState.value = _uiState.value.copy(
            selectedTrackIds = _uiState.value.selectedTrackIds + tracks.map { it.id }
        )
    }

    fun onClearSelection() {
        if (_uiState.value.selectedTrackIds.isEmpty()) return
        _uiState.value = _uiState.value.copy(selectedTrackIds = emptySet())
    }

    fun selectedTracks(from: List<Track>): List<Track> {
        val ids = _uiState.value.selectedTrackIds
        return from.filter { it.id in ids }
    }

    fun onAddTracksToPlaylist(playlistId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        tracks.forEach { libraryRepository.addToPlaylist(playlistId, it) }
        refreshLibrary()
        autoPushSync()
    }

    fun onRemoveTracksFromPlaylist(playlistId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val playlist = _uiState.value.playlists.firstOrNull { it.id == playlistId } ?: return
        tracks.forEach { wanted ->
            val key = wanted.matchKey()

            val existing = playlist.tracks.firstOrNull { it.id == wanted.id || it.matchKey() == key }
            if (existing != null) libraryRepository.removeFromPlaylist(playlistId, existing.id)
        }
        refreshLibrary()
        autoPushSync()
    }

    fun onCreatePlaylistWith(name: String, tracks: List<Track>) {
        val created = libraryRepository.createPlaylist(name) ?: return
        tracks.forEach { libraryRepository.addToPlaylist(created.id, it) }
        refreshLibrary()
        autoPushSync()
    }

    fun onCreatePlaylist(name: String) {
        libraryRepository.createPlaylist(name) ?: return
        refreshLibrary()
    }

    fun onDeletePlaylist(id: String) {
        libraryRepository.deletePlaylist(id)
        refreshLibrary()
    }

    fun onRenamePlaylist(id: String, newName: String) {
        libraryRepository.renamePlaylist(id, newName)
        refreshLibrary()
    }

    fun onMovePlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        libraryRepository.movePlaylistTrack(playlistId, fromIndex, toIndex)
        refreshLibrary()
    }

    fun onMoveQueueTrack(fromIndex: Int, toIndex: Int) {
        val q = _uiState.value.queue.toMutableList()
        if (fromIndex !in q.indices || toIndex !in q.indices || fromIndex == toIndex) return
        val item = q.removeAt(fromIndex)
        q.add(toIndex, item)
        val cur = _uiState.value.queueIndex
        val newIndex = when {
            cur == fromIndex -> toIndex
            fromIndex < cur && toIndex >= cur -> cur - 1
            fromIndex > cur && toIndex <= cur -> cur + 1
            else -> cur
        }
        _uiState.value = _uiState.value.copy(queue = q, queueIndex = newIndex)
        preloadNext()
        saveSession()
    }

    fun onMoveFavorite(fromIndex: Int, toIndex: Int) {
        libraryRepository.moveFavorite(fromIndex, toIndex)
        refreshLibrary()
    }

    fun onMovePlaylist(fromIndex: Int, toIndex: Int) {
        libraryRepository.movePlaylist(fromIndex, toIndex)
        refreshLibrary()
    }

    fun onMoveBrowseTrack(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.browseTracks.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        list.add(toIndex, list.removeAt(fromIndex))
        _uiState.value = _uiState.value.copy(browseTracks = list.distinctById())
    }

    fun onMoveLocalTrack(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.localTracks.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) return
        list.add(toIndex, list.removeAt(fromIndex))
        _uiState.value = _uiState.value.copy(localTracks = list)
    }

    fun exportPlaylistJson(id: String): String = libraryRepository.exportPlaylistJson(id).orEmpty()

    fun onImportPlaylist(text: String) {
        libraryRepository.importPlaylistJson(text) ?: return
        refreshLibrary()
    }

    fun onAddToPlaylist(playlistId: String, track: Track) {
        libraryRepository.addToPlaylist(playlistId, track)
        refreshLibrary()
    }

    fun onRemoveFromPlaylist(playlistId: String, trackId: String) {
        libraryRepository.removeFromPlaylist(playlistId, trackId)
        refreshLibrary()
    }

    fun onExperimentalPluginToggled(enabled: Boolean) {
        settingsRepository.experimentalPluginEngineEnabled = enabled
        _uiState.value = _uiState.value.copy(experimentalPluginEnabled = enabled)
    }

    fun onHighQualityArtToggled(enabled: Boolean) {
        settingsRepository.highQualityArtEnabled = enabled
        _uiState.value = _uiState.value.copy(highQualityArt = enabled)
    }

    fun onToggleSource(id: String, enabled: Boolean) {
        pluginManager.setEnabled(id, enabled)
        _uiState.value = _uiState.value.copy(sources = pluginManager.all(), servers = serverEntries(), serverHost = libraryServerUrl().orEmpty())
    }

    fun onSaveExtensionSettings(pluginId: String, values: Map<String, String>) {
        pluginManager.saveExtensionSettings(pluginId, values)
        _uiState.value = _uiState.value.copy(sources = pluginManager.all(), servers = serverEntries(), serverHost = libraryServerUrl().orEmpty())
    }

    fun onExtensionsChanged() {
        pluginManager.rescanExtensions()
        _uiState.value = _uiState.value.copy(sources = pluginManager.all(), servers = serverEntries(), serverHost = libraryServerUrl().orEmpty())
    }

    fun onInstallSource(name: String, url: String) {
        pluginManager.install(name, url) ?: return
        _uiState.value = _uiState.value.copy(
            sources = pluginManager.all(),
            servers = serverEntries(),
            serverHost = libraryServerUrl().orEmpty(),
            backendTestStatus = null
        )
    }

    fun onUninstallSource(id: String) {
        pluginManager.uninstall(id)
        _uiState.value = _uiState.value.copy(sources = pluginManager.all(), servers = serverEntries(), serverHost = libraryServerUrl().orEmpty())
    }

    fun onRenameSource(id: String, newName: String) {
        pluginManager.rename(id, newName)
        _uiState.value = _uiState.value.copy(sources = pluginManager.all(), servers = serverEntries(), serverHost = libraryServerUrl().orEmpty())
    }

    fun onUpdateSource(id: String, newName: String, newBaseUrl: String) {
        pluginManager.update(id, newName, newBaseUrl)
        _uiState.value = _uiState.value.copy(sources = pluginManager.all(), servers = serverEntries(), serverHost = libraryServerUrl().orEmpty())
    }

    fun onSaveServer(id: String?, name: String, address: String, username: String, password: String) {
        var url = address.trim().trimEnd('/')
        if (url.substringAfter("://").isBlank()) {
            _uiState.value = _uiState.value.copy(serverConfigStatus = "✗ Enter the server's address")
            return
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://$url"
        val servers = pluginManager.servers()
        val serverId = id
            ?: servers.firstOrNull { it.baseUrl.trimEnd('/') == url }?.id
            ?: if (servers.none { it.id == FIRST_SERVER_ID }) FIRST_SERVER_ID else "server_" + url.hashCode().toUInt().toString(16)
        val label = name.trim().ifBlank { servers.firstOrNull { it.id == serverId }?.name ?: serverLabel(url) }

        pluginManager.uninstall("server_media")
        pluginManager.upsert(serverId, label, url)
        settingsRepository.saveSignIn(serverId, username.trim(), password)
        if (serverId == FIRST_SERVER_ID) {
            val port = url.substringAfter("://").substringAfter(':', "").substringBefore('/')
            settingsRepository.serverHost = if (port.isEmpty()) url else url.removeSuffix(":$port")
            settingsRepository.serverLibraryPort = port
        }

        _uiState.value = _uiState.value.copy(
            sources = pluginManager.all(),
            servers = serverEntries(),
            serverHost = libraryServerUrl().orEmpty(),
            serverConfigStatus = "Checking $label…"
        )
        downServers.remove(url)

        viewModelScope.launch {
            val access = repository.checkServerAccess(url)
            val entered = username.isNotBlank() || password.isNotEmpty()
            _uiState.value = _uiState.value.copy(serverConfigStatus = "$label: " + serverAccessMessage(access, entered))
            if (access != ServerAccess.OPEN && access != ServerAccess.SIGNED_IN) return@launch

            refreshServerLibrary()

            if (url == libraryServerUrl() && settingsRepository.syncAutoEnabled) {
                repository.getPlaylistSync(url)?.let { applyRemoteSync(it) }
                repository.putPlaylistSync(url, buildSyncState())
            }
        }
    }

    fun onRemoveServer(id: String) {
        pluginManager.uninstall(id)
        settingsRepository.forgetSignIn(id)
        if (id == FIRST_SERVER_ID) settingsRepository.serverHost = ""
        _uiState.value = _uiState.value.copy(
            sources = pluginManager.all(),
            servers = serverEntries(),
            serverHost = libraryServerUrl().orEmpty(),
            serverConfigStatus = null
        )
        refreshServerLibrary()
    }

    fun onMoveServer(id: String, up: Boolean) {
        pluginManager.moveServer(id, up)
        _uiState.value = _uiState.value.copy(
            sources = pluginManager.all(),
            servers = serverEntries(),
            serverHost = libraryServerUrl().orEmpty()
        )
        refreshServerLibrary()
    }

    private fun serverLabel(url: String): String =
        url.substringAfter("://").substringBefore('/').ifBlank { "Server" }

    private fun serverAccessMessage(access: ServerAccess, signInEntered: Boolean): String = when (access) {
        ServerAccess.OPEN ->
            if (signInEntered) "✓ Server configured. It doesn't ask for a sign-in, so the username and password aren't used."
            else "✓ Server configured."
        ServerAccess.SIGNED_IN -> "✓ Server configured and signed in."
        ServerAccess.NEEDS_SIGN_IN -> "✗ This server asks for a username and password. Enter the ones set on the server."
        ServerAccess.WRONG_SIGN_IN -> "✗ The server refused that username or password."
        ServerAccess.LOCKED_OUT -> "✗ Too many wrong sign-ins from this connection. Wait 10 minutes, then try again."
        ServerAccess.UNREACHABLE -> "✗ Saved, but the server didn't answer. Check the address and port, and that it's running."
    }

    private fun libraryServerUrl(): String? = serverBasesInOrder().firstOrNull()

    private fun serverBasesInOrder(): List<String> =
        pluginManager.servers().filter { it.enabled }.map { it.baseUrl.trim().trimEnd('/') }.filter { it.isNotBlank() }

    private fun serverEntries(): List<ServerEntry> =
        pluginManager.servers().map { server ->
            val (username, password) = settingsRepository.signInFor(server.id)
            ServerEntry(server.id, server.name, server.baseUrl, username, password, server.enabled)
        }

    fun onLoadServerFolders() {
        val base = libraryServerUrl()
        if (base == null) {
            _uiState.value = _uiState.value.copy(
                serverConfigStatus = "✗ Set your server Host + Library port first."
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(serverFoldersLoading = true)
            val status = try {
                val folders = repository.listServerFolders(base)
                _uiState.value = _uiState.value.copy(serverFolders = folders)
                if (folders.isEmpty()) {
                    "Server reachable, but there are no folders yet. Type a name and it's created on the first upload."
                } else {
                    "✓ Loaded ${folders.size} folder(s) from $base."
                }
            } catch (e: Exception) {
                "✗ ${e.message ?: "Couldn't reach the library server."} " +
                    "(Is the Library port open in Oracle's security list and the server's firewall?)"
            }
            _uiState.value = _uiState.value.copy(
                serverFoldersLoading = false,
                serverConfigStatus = status
            )
        }
    }

    fun onSetDefaultServerDir(dir: String) {
        settingsRepository.defaultServerDir = dir
        _uiState.value = _uiState.value.copy(defaultServerDir = dir)
    }

    fun onToggleSettingsLock(locked: Boolean) {
        settingsRepository.settingsLocked = locked
        _uiState.value = _uiState.value.copy(settingsLocked = locked)
    }

    fun onReorderSettings(order: List<String>) {
        settingsRepository.settingsOrderCsv = order.joinToString(",")
        _uiState.value = _uiState.value.copy(settingsSectionOrder = order)
        autoPushSync()
    }

    fun onSelectTheme(id: String) {
        themeRegistry.select(id)
        _uiState.value = _uiState.value.copy(selectedThemeId = themeRegistry.selectedId())
        autoPushSync()
    }

    fun onInstallTheme(
        name: String,
        primary: String,
        secondary: String,
        tertiary: String,
        background: String,
        dark: Boolean
    ) {
        themeRegistry.install(name, primary, secondary, tertiary, background, dark) ?: return
        _uiState.value = _uiState.value.copy(
            themes = themeRegistry.all(),
            selectedThemeId = themeRegistry.selectedId()
        )
        autoPushSync()
    }

    fun onUninstallTheme(id: String) {
        themeRegistry.uninstall(id)
        _uiState.value = _uiState.value.copy(
            themes = themeRegistry.all(),
            selectedThemeId = themeRegistry.selectedId()
        )
    }

    private val importJson = Json { ignoreUnknownKeys = true }

    fun onImportPlugin(jsonText: String) {
        val bundle = try {
            importJson.decodeFromString<PluginBundle>(jsonText)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                importStatus = "✗ Invalid plugin file: ${e.message ?: "could not parse JSON"}"
            )
            return
        }

        var added = 0
        bundle.sources.forEach { s ->
            if (pluginManager.install(s.name, s.baseUrl) != null) added++
        }
        bundle.themes.forEach { t ->
            val installed = themeRegistry.install(
                t.name, t.primary, t.secondary, t.tertiary, t.background, t.dark
            )
            if (installed != null) added++
        }

        _uiState.value = _uiState.value.copy(
            sources = pluginManager.all(),
            themes = themeRegistry.all(),
            selectedThemeId = themeRegistry.selectedId(),
            importStatus = if (added > 0) {
                "✓ Imported $added item(s)."
            } else {
                "✗ Nothing valid to import in that file."
            }
        )
    }

    fun onConfirmPlaylistImport(playlistName: String) {
        val preview = _uiState.value.playlistImportPreview ?: return
        val name = playlistName.ifBlank { preview.sourceName }
        val playlist = libraryRepository.createPlaylist(name) ?: return

        libraryRepository.addTracksToPlaylist(playlist.id, preview.matched)
        refreshLibrary()
        _uiState.value = _uiState.value.copy(
            playlistImportPreview = null,
            playlistImportStatus = "✓ Created \"$name\" with ${preview.matched.size} tracks."
        )
    }

    fun onCancelPlaylistImport() {
        val status = _uiState.value.playlistImportStatus
        val running = _uiState.value.playlistImportPreview == null &&
            (status?.startsWith("Matching") == true || status?.startsWith("Reading") == true)
        if (running) cancelTransfer()
        _uiState.value = _uiState.value.copy(playlistImportPreview = null, playlistImportStatus = null)
    }

    fun onImportPlaylistLink(url: String) {
        if (url.isBlank()) return
        enqueueTransfer {
            beginTransfer("Opening playlist…")
            _uiState.value = _uiState.value.copy(
                playlistImportStatus = "Opening link…",
                playlistImportPreview = null
            )
            val result = try {
                repository.importPlaylistLink(url) { done, total ->
                    updateTransferProgress(if (total > 0) done.toFloat() / total else -1f)
                    _uiState.value = _uiState.value.copy(playlistImportStatus = "Matching $done of $total…")
                }
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(playlistImportStatus = "Import cancelled.", playlistImportPreview = null)
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(playlistImportStatus = "✗ ${e.message ?: e}")
                finishTransfer("Import failed")
                return@enqueueTransfer
            }
            _uiState.value = _uiState.value.copy(
                playlistImportStatus = "Matched ${result.matched.size} of ${result.totalCount} tracks.",
                playlistImportPreview = result
            )
            finishTransfer("Matched ${result.matched.size} tracks")
        }
    }

    fun onImportCsvPlaylist(csvText: String, playlistName: String = "") {
        enqueueTransfer {
            beginTransfer("Matching playlist…")
            _uiState.value = _uiState.value.copy(
                playlistImportStatus = "Reading CSV…",
                playlistImportPreview = null
            )
            val result = try {
                repository.importCsvPlaylist(csvText, playlistName) { done, total ->
                    updateTransferProgress(if (total > 0) done.toFloat() / total else -1f)
                    _uiState.value = _uiState.value.copy(
                        playlistImportStatus = "Matching $done of $total…"
                    )
                }
            } catch (e: CancellationException) {
                _uiState.value = _uiState.value.copy(
                    playlistImportStatus = "Import cancelled.",
                    playlistImportPreview = null
                )
                throw e
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(playlistImportStatus = "✗ ${e.message ?: e}")
                finishTransfer("Import failed")
                return@enqueueTransfer
            }
            _uiState.value = _uiState.value.copy(
                playlistImportStatus = "Matched ${result.matched.size} of " +
                    "${result.matched.size + result.unmatchedCount} tracks.",
                playlistImportPreview = result
            )
            finishTransfer("Matched ${result.matched.size} tracks")
        }
    }

    fun exportPluginsJson(): String {
        val state = _uiState.value
        val sources = state.sources
            .filter { !it.builtIn && it.kind == SourceKind.SERVER }
            .map { ImportedSource(it.name, it.baseUrl) }
        val themes = state.themes
            .filter { !it.builtIn }
            .map { ImportedTheme(it.name, it.primary, it.secondary, it.tertiary, it.background, it.dark) }
        return importJson.encodeToString(PluginBundle(sources, themes))
    }

    fun onTestBackend(url: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(backendTestStatus = "Testing…")
            val status = try {
                repository.testBackend(url)
                "✓ Connected: backend is working."
            } catch (e: SearchUnavailableException) {
                "✗ ${e.message}"
            } catch (e: Exception) {
                "✗ ${e.message ?: e.toString()}"
            }
            _uiState.value = _uiState.value.copy(backendTestStatus = status)
        }
    }
}
