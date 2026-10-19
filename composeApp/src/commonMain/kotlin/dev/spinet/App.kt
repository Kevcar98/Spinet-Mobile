package dev.spinet

import dev.spinet.data.savedServerSignIns
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import dev.spinet.audio.AudioPlayer
import dev.spinet.audio.PlaybackState
import dev.spinet.data.NetworkRepository
import dev.spinet.data.createPlatformHttpClient
import dev.spinet.download.FileDownloader
import dev.spinet.library.LibraryRepository
import dev.spinet.navigation.Destination
import dev.spinet.plugins.PluginManager
import dev.spinet.theme.ThemeRegistry
import dev.spinet.theme.toColorScheme
import dev.spinet.settings.SettingsRepository
import dev.spinet.model.Playlist
import dev.spinet.model.SourceKind
import dev.spinet.model.SourcePlugin
import dev.spinet.model.Track
import dev.spinet.local.PickLocalFolderButton
import dev.spinet.ui.screens.AddToPlaylistDialog
import dev.spinet.ui.screens.BrowsePlaylistsScreen
import dev.spinet.ui.screens.IdentifySongAction
import dev.spinet.ui.screens.LibraryScreen
import androidx.compose.runtime.CompositionLocalProvider
import dev.spinet.ui.screens.LocalHighQualityArt
import dev.spinet.ui.SaveJsonFileButton
import dev.spinet.ui.screens.LocalFolderEntry
import dev.spinet.ui.screens.LocalFoldersScreen
import dev.spinet.ui.screens.LocalMusicScreen
import dev.spinet.ui.screens.NowPlayingScreen
import dev.spinet.ui.screens.PlaylistDetailScreen
import dev.spinet.ui.screens.SearchScreen
import dev.spinet.ui.screens.SettingsScreen
import dev.spinet.ui.screens.SupportScreen
import dev.spinet.ui.screens.TrackThumbnail
import dev.spinet.model.looseKey
import dev.spinet.model.matchKey
import dev.spinet.viewmodel.MainViewModel
import dev.spinet.viewmodel.RepeatMode
import androidx.compose.runtime.LaunchedEffect
import dev.spinet.ui.screens.UpdateDialog
import dev.spinet.update.createUpdateInstaller
import dev.spinet.APP_VERSION
import dev.spinet.local.defaultLocalFolder
import dev.spinet.ui.screens.UpdateBanner

private const val LOCAL_ALL_TRACKS = "__all_local__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    audioPlayer: AudioPlayer,
    fileDownloader: FileDownloader,
    settingsRepository: SettingsRepository = remember { SettingsRepository() },
    pluginManager: PluginManager = remember { PluginManager(settingsRepository) },
    themeRegistry: ThemeRegistry = remember { ThemeRegistry(settingsRepository) },
    libraryRepository: LibraryRepository = remember { LibraryRepository(settingsRepository) },
    repository: NetworkRepository = remember {
        NetworkRepository(
            client = createPlatformHttpClient(),
            sourcesProvider = { pluginManager.enabledOrdered() },
            extensionSources = { pluginManager.extensions.sources },
            serverSignIns = { settingsRepository.savedServerSignIns() }
        )
    },
    viewModel: MainViewModel = viewModel {
        MainViewModel(
            audioPlayer, repository, settingsRepository,
            pluginManager, themeRegistry, fileDownloader, libraryRepository,
            createUpdateInstaller()
        )
    }
) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    val state by viewModel.uiState.collectAsState()
    val extensionRepositories = remember {
        dev.spinet.extensions.extensionPlatform?.let {
            dev.spinet.extensions.ExtensionRepositories(
                settingsRepository,
                repository.httpClient,
                installedVersions = { pluginManager.extensions.sources.associate { s -> s.id to s.versionCode } },
                onExtensionsChanged = viewModel::onExtensionsChanged
            )
        }
    }
    val colorScheme = state.themes
        .firstOrNull { it.id == state.selectedThemeId }
        ?.toColorScheme()
        ?: lightColorScheme()

    MaterialTheme(colorScheme = colorScheme) {
      CompositionLocalProvider(LocalHighQualityArt provides state.highQualityArt) {
        var current by remember { mutableStateOf(Destination.Library) }

        var libraryDetail by remember { mutableStateOf<String?>(null) }

        var browseSource by remember { mutableStateOf<SourcePlugin?>(null) }
        var browseFolderTitle by remember { mutableStateOf<String?>(null) }

        var localFolder by remember { mutableStateOf<String?>(null) }
        var showNowPlaying by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { viewModel.onCheckForUpdates() }

        state.availableUpdate?.takeIf { state.updateDialogOpen }?.let { update ->
            UpdateDialog(
                update = update,
                busy = state.updateBusy,
                error = state.updateError,
                onInstall = viewModel::onInstallUpdate,
                onDismiss = viewModel::onDismissUpdate
            )
        }

        var addTarget by remember { mutableStateOf<Track?>(null) }

        var addSelection by remember { mutableStateOf(false) }

        val defaultFolder = remember { defaultLocalFolder() }
        var showFolderPrompt by remember {
            mutableStateOf(settingsRepository.localFolderUri.isBlank())
        }
        if (showFolderPrompt) {
            AlertDialog(
                onDismissRequest = { showFolderPrompt = false },
                title = { Text("Where should music be saved?") },
                text = {
                    Text(
                        if (defaultFolder != null) {
                            "Downloads and your offline library live in this folder. " +
                                "Use ${defaultFolder.label}, or pick your own. You can " +
                                "change this later in Settings."
                        } else {
                            "Pick a folder on your phone that holds your music so it plays here, even offline. You can change this later in Settings."
                        }
                    )
                },
                confirmButton = {
                    PickLocalFolderButton(text = "Choose folder") { folder ->
                        viewModel.onPickLocalFolder(folder)
                        settingsRepository.localFolderPrompted = true
                        showFolderPrompt = false
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showFolderPrompt = false }) { Text("Not now") }
                        if (defaultFolder != null) {
                            TextButton(onClick = {
                                viewModel.onPickLocalFolder(defaultFolder)
                                settingsRepository.localFolderPrompted = true
                                showFolderPrompt = false
                            }) { Text("Use ${defaultFolder.label}") }
                        }
                    }
                }
            )
        }

        val browsableSources = state.sources.filter {
            it.enabled && it.kind == SourceKind.SERVER
        }

        val deviceKeys = remember(state.localTracks) {
            (state.localTracks.map { it.matchKey() } +
                state.localTracks.mapNotNull { it.looseKey() }).toSet()
        }
        val isOnDevice: (Track) -> Boolean = { track ->
            track.matchKey() in deviceKeys || track.looseKey()?.let { it in deviceKeys } == true
        }

        val serverKeys = remember(state.favorites, state.playlists, state.browseTracks, state.serverLibraryKeys) {
            val onServer = (state.favorites +
                state.playlists.flatMap { it.tracks } +
                state.browseTracks)
                .filter { it.serverBaseUrl.isNotBlank() }
            (onServer.map { it.matchKey() } +
                onServer.mapNotNull { it.looseKey() }).toSet() + state.serverLibraryKeys
        }
        val isOnServer: (Track) -> Boolean = { track ->
            track.serverBaseUrl.isNotBlank() || track.matchKey() in serverKeys ||
                track.looseKey()?.let { it in serverKeys } == true
        }

        val localFolderEntries = remember(state.localTracks) {
            state.localTracks
                .filter { it.localFolder.isNotBlank() }
                .groupingBy { it.localFolder }
                .eachCount()
                .map { (name, count) -> LocalFolderEntry(name, count) }
                .sortedBy { it.name.lowercase() }
        }
        val localShownTracks = when (localFolder) {
            null -> emptyList()
            LOCAL_ALL_TRACKS -> state.localTracks
            else -> state.localTracks.filter { it.localFolder == localFolder }
        }

        if (showNowPlaying) {
            PlatformBackHandler(enabled = true) { showNowPlaying = false }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
            NowPlayingScreen(
                track = state.currentTrack,
                playback = state.playback,
                isFavorite = state.currentTrack?.id?.let { it in state.favoriteTrackIds } ?: false,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                hasNext = state.queue.isNotEmpty() && (
                    state.queueIndex < state.queue.size - 1 ||
                        state.shuffle || state.repeatMode != RepeatMode.OFF
                    ),
                hasPrevious = state.hasPrevious || state.positionMs > 3_000,
                hasPreviousTrack = state.hasPrevious,
                shuffle = state.shuffle,
                repeatMode = state.repeatMode,
                onToggleShuffle = viewModel::onToggleShuffle,
                onCycleRepeat = viewModel::onCycleRepeat,
                onClose = { showNowPlaying = false },
                onPlay = viewModel::onPlayClicked,
                onPause = viewModel::onPauseClicked,
                onNext = viewModel::onNext,
                onPrevious = viewModel::onPrevious,
                onPreviousTrack = viewModel::onPreviousTrack,
                onSeek = viewModel::onSeek,
                onToggleFavorite = { state.currentTrack?.let(viewModel::onToggleFavorite) },
                onAddToPlaylist = { addTarget = state.currentTrack },
                canDownload = state.currentTrack?.downloadable == true,
                onDownload = { state.currentTrack?.let(viewModel::onDownloadClicked) },
                alreadyOnDevice = state.currentTrack?.let(isOnDevice) ?: false,
                alreadyOnServer = state.currentTrack?.let(isOnServer) ?: false,
                serverTargets = browsableSources,
                onUploadToServer = { baseUrl ->
                    state.currentTrack?.let { viewModel.onUploadPlaylistToServer(listOf(it), baseUrl) }
                },
                transferProgress = state.transferProgress,
                transferLabel = state.transferLabel?.let { base ->

                    if (state.transferQueueDepth > 0) "$base (+${state.transferQueueDepth} queued)" else base
                },
                onCancelTransfer = viewModel::cancelTransfer,
                sleepTimerRemainingMs = state.sleepTimerRemainingMs,
                sleepAtEndOfTrack = state.sleepAtEndOfTrack,
                onSetSleepTimer = viewModel::onSetSleepTimer,
                onToggleSleepAtEnd = viewModel::onToggleSleepAtEndOfTrack,
                queue = state.queue,
                queueIndex = state.queueIndex,
                onPlayFromQueue = { track -> viewModel.onTrackClicked(track, state.queue) },
                onMoveQueueTrack = { from, to -> viewModel.onMoveQueueTrack(from, to) }
            )
            }
            addTarget?.let { target ->
                AddToPlaylistDialog(
                    playlists = state.playlists,
                    tracks = listOf(target),
                    onDismiss = { addTarget = null },

                    onAdd = { id, tracks -> viewModel.onAddTracksToPlaylist(id, tracks) },
                    onRemove = { id, tracks -> viewModel.onRemoveTracksFromPlaylist(id, tracks) },
                    onCreate = { name, tracks -> viewModel.onCreatePlaylistWith(name, tracks) }
                )
            }
            return@CompositionLocalProvider
        }

        val libraryCanPop = current == Destination.Library &&
            (browseFolderTitle != null || browseSource != null || libraryDetail != null)
        PlatformBackHandler(enabled = current != Destination.Library || libraryCanPop) {
            when {
                current != Destination.Library -> current = Destination.Library
                browseFolderTitle != null -> browseFolderTitle = null
                browseSource != null -> browseSource = null

                localFolder != null -> localFolder = null
                else -> libraryDetail = null
            }
        }

        Scaffold(
            snackbarHost = {
                state.availableUpdate
                    ?.takeIf { !state.updateDismissed && !state.updateDialogOpen }
                    ?.let { update ->
                        UpdateBanner(
                            update = update,
                            busy = state.updateBusy,
                            error = state.updateError,
                            onInstall = viewModel::onInstallUpdate,
                            onIgnore = viewModel::onDismissUpdate,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
            },
            topBar = {
                TopAppBar(
                    title = { Text(current.label) },
                    actions = {
                        val browsingFolder = current == Destination.Library &&
                            browseSource != null && browseFolderTitle != null

                        val openPlaylist = if (current == Destination.Library &&
                            browseSource == null && libraryDetail != null &&
                            libraryDetail != "local" && libraryDetail != "favorites"
                        ) state.playlists.firstOrNull { it.id == libraryDetail } else null

                        val onLocal = current == Destination.Library &&
                            browseSource == null && libraryDetail == "local" &&
                            state.localFolderLabel.isNotBlank()
                        val onLocalTracks = onLocal && localFolder != null
                        when {
                            browsingFolder -> {
                                if (state.browseTracks.isNotEmpty()) {
                                    FilledIconButton(
                                        onClick = { viewModel.onPlayAll(state.browseTracks) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                                    }
                                }
                                IconButton(onClick = { viewModel.onRefreshBrowse() }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                                }
                            }

                            openPlaylist != null -> {
                                if (openPlaylist.tracks.isNotEmpty()) {
                                    FilledIconButton(
                                        onClick = { viewModel.onPlayAll(openPlaylist.tracks) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                                    }
                                }
                                SaveJsonFileButton(
                                    label = "Export",
                                    suggestedName = "${openPlaylist.name}.json",
                                    content = { viewModel.exportPlaylistJson(openPlaylist.id) }
                                )
                            }

                            onLocal -> {
                                if (onLocalTracks && localShownTracks.isNotEmpty()) {
                                    FilledIconButton(
                                        onClick = { viewModel.onPlayAll(localShownTracks) },
                                        modifier = Modifier.padding(end = 4.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                                    }
                                }
                                IconButton(onClick = { viewModel.onRefreshLocalWithMetadata() }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Rescan folder")
                                }
                            }
                        }
                    }
                )
            },
            bottomBar = {
                Column {
                    TransferBar(
                        progress = state.transferProgress,
                        label = state.transferLabel?.let { base ->

                    if (state.transferQueueDepth > 0) "$base (+${state.transferQueueDepth} queued)" else base
                },
                        onCancel = viewModel::cancelTransfer
                    )
                    PlaybackBar(
                        title = state.currentTrack?.title ?: "Nothing playing",
                        thumbnailUrl = state.currentTrack?.thumbnailUrl ?: "",
                        playback = state.playback,
                        hasTrack = state.currentTrack != null,
                        hasNext = state.queue.isNotEmpty() && (
                            state.queueIndex < state.queue.size - 1 ||
                                state.shuffle || state.repeatMode != RepeatMode.OFF
                            ),
                        hasPrevious = state.hasPrevious || state.positionMs > 3_000,
                        onOpen = { showNowPlaying = true },
                        onPlay = viewModel::onPlayClicked,
                        onPause = viewModel::onPauseClicked,
                        onNext = viewModel::onNext,
                        onPrevious = viewModel::onPrevious
                    )
                    NavigationBar {
                        Destination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = current == destination,
                                onClick = { current = destination },
                                icon = {
                                    Icon(destination.icon, contentDescription = destination.label)
                                },
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)

            val nowKey = state.currentTrack?.matchKey()
            val nowProgress = if (state.durationMs > 0L)
                state.positionMs.toFloat() / state.durationMs else 0f

            val onRowClick: (Track, List<Track>) -> Unit = { track, list ->
                if (nowKey != null && track.matchKey() == nowKey) showNowPlaying = true
                else viewModel.onTrackClicked(track, list)
            }
            when (current) {
                Destination.Search -> SearchScreen(
                    query = state.searchQuery,
                    results = state.searchResults,
                    isSearching = state.isSearching,
                    error = state.searchError,
                    downloadStatus = state.downloadStatus,
                    favoriteTrackIds = state.favoriteTrackIds,
                    serverTargets = browsableSources,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onTrackClick = { track ->

                        viewModel.onSearchCommitted(state.searchQuery)
                        onRowClick(track, state.searchResults)
                    },
                    onDownloadToPhone = viewModel::onDownloadClicked,
                    onDownloadToServer = { track, baseUrl -> viewModel.onDownloadTrackToServer(track, baseUrl) },
                    onToggleFavorite = viewModel::onToggleFavorite,
                    onAddToPlaylist = { addTarget = it },
                    onDeleteFromServer = { track -> viewModel.onDeleteFromServer(track) },
                    isOnDevice = isOnDevice,
                    isOnServer = isOnServer,
                    currentTrackKey = nowKey,
                    playingProgress = nowProgress,
                    searchHistory = state.searchHistory,
                    onClearHistory = viewModel::onClearSearchHistory,
                    onRemoveHistory = viewModel::onRemoveSearchHistory,
                    identifyContent = {
                        if (repository.canRecognize()) {
                        IdentifySongAction(
                            phase = state.identifyPhase,
                            result = state.identifyResult,
                            error = state.identifyError,
                            sources = state.identifySources,
                            onStart = viewModel::onStartIdentify,
                            onDismiss = viewModel::onDismissIdentify,
                            onSearchResult = viewModel::onSearchIdentified,
                        )
                    }
                    },
                    modifier = contentModifier
                )

                Destination.Library -> {
                    val browsing = browseSource
                    val detail = libraryDetail
                    when {
                        browsing != null && browseFolderTitle != null -> PlaylistDetailScreen(
                            title = browseFolderTitle!!,
                            tracks = state.browseTracks,
                            favoriteTrackIds = state.favoriteTrackIds,
                            isFavoritesList = false,
                            onBack = { browseFolderTitle = null },
                            onTrackClick = { track -> onRowClick(track, state.browseTracks) },
                            onPlayAll = { viewModel.onPlayAll(state.browseTracks) },
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onAddToPlaylist = { addTarget = it },
                            selectedTrackIds = state.selectedTrackIds,
                            onToggleSelect = viewModel::onToggleSelect,
                            onClearSelection = viewModel::onClearSelection,
                            onSelectAll = viewModel::onSelectAll,
                            onAddSelectionToPlaylist = { addSelection = true },
                            onRemove = null,
                            onDelete = null,
                            serverTargets = browsableSources,
                            onDownloadAll = { viewModel.onDownloadPlaylistToDevice(state.browseTracks, browseFolderTitle ?: "") },
                            onDeleteAll = { viewModel.onDeleteAllFromServer(state.browseTracks) },
                            onSaveTrackToDevice = { track -> viewModel.onDownloadClicked(track) },
                            onUploadTrackToServer = { track, baseUrl -> viewModel.onUploadPlaylistToServer(listOf(track), baseUrl) },
                            onDeleteTrackFromServer = { track -> viewModel.onDeleteFromServer(track) },
                                        onDeleteTrackFromDevice = { track -> viewModel.onDeleteLocalCopy(track) },
                            onPickVersion = { track -> viewModel.onPickVersion(track) },

                            onRefresh = null,
                            showPlayAll = false,
                            onMove = { from, to -> viewModel.onMoveBrowseTrack(from, to) },
                            isOnDevice = isOnDevice,
                            isOnServer = isOnServer,
                            currentTrackKey = nowKey,
                            playingProgress = nowProgress,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            onToggleShuffle = viewModel::onToggleShuffle,
                            onCycleRepeat = viewModel::onCycleRepeat,
                            modifier = contentModifier
                        )

                        browsing != null -> BrowsePlaylistsScreen(
                            sourceName = browsing.name,
                            playlists = state.browsePlaylists,
                            loading = state.browseLoading,
                            error = state.browseError,
                            onBack = { browseSource = null },
                            onSearch = { query ->

                                browseFolderTitle = "Search: $query"
                                viewModel.onSearchBrowseSource(browsing.baseUrl, query)
                            },
                            onOpenPlaylist = { pl ->
                                browseFolderTitle = pl.name
                                viewModel.onOpenBrowsePlaylist(browsing.baseUrl, pl.id)
                            },
                            onPlayPlaylist = { pl ->
                                viewModel.onPlayBrowsePlaylist(browsing.baseUrl, pl.id)
                            },
                            onDeletePlaylist = { pl ->
                                viewModel.onDeleteBrowseFolder(browsing.baseUrl, pl.id)
                            },
                            modifier = contentModifier
                        )

                        detail == null -> LibraryScreen(
                            playlists = state.playlists,
                            favoritesCount = state.favorites.size,
                            browsableSources = browsableSources,
                            localFolderLabel = state.localFolderLabel,
                            localTrackCount = state.localTracks.size,
                            onOpenFavorites = { libraryDetail = "favorites" },
                            mostPlayedCount = state.mostPlayed.size,
                            onOpenMostPlayed = { libraryDetail = "mostplayed" },
                            onOpenPlaylist = { libraryDetail = it },
                            onPlayPlaylist = { pl -> viewModel.onPlayAll(pl.tracks) },
                            onOpenLocal = { libraryDetail = "local"; localFolder = null },
                            onBrowseSource = { source ->
                                browseSource = source
                                browseFolderTitle = null
                                viewModel.onBrowseSource(source.baseUrl)
                            },
                            onCreatePlaylist = viewModel::onCreatePlaylist,
                            onImportPlaylist = viewModel::onImportPlaylist,
                            onRenamePlaylist = viewModel::onRenamePlaylist,
                            onDeletePlaylist = viewModel::onDeletePlaylist,
                            onMovePlaylist = viewModel::onMovePlaylist,
                            onDownloadPlaylist = { pl ->
                                viewModel.onDownloadPlaylistToDevice(pl.tracks, pl.name)
                            },

                            onUploadPlaylist = browsableSources.firstOrNull()?.let { server ->
                                { pl: Playlist ->
                                    viewModel.onUploadPlaylistToServer(pl.tracks, server.baseUrl, pl.name)
                                }
                            },
                            exportPlaylistJson = { pl -> viewModel.exportPlaylistJson(pl.id) },
                            syncAvailable = state.serverHost.isNotBlank(),
                            onSyncPlaylists = viewModel::onSyncPlaylistsToServer,
                            onFetchPlaylists = viewModel::onFetchPlaylistsFromServer,
                            modifier = contentModifier
                        )

                        detail == "local" && localFolder == null -> LocalFoldersScreen(
                            title = "On this device",
                            allCount = state.localTracks.size,
                            folders = localFolderEntries,
                            loading = state.localLoading,
                            onBack = { libraryDetail = null },
                            onOpenAll = { localFolder = LOCAL_ALL_TRACKS },
                            onPlayAll = { viewModel.onPlayAll(state.localTracks) },
                            onOpenFolder = { localFolder = it },
                            onPlayFolder = { name ->
                                viewModel.onPlayAll(state.localTracks.filter { it.localFolder == name })
                            },
                            onDeleteFolder = { name -> viewModel.onDeleteLocalFolder(name) },
                            modifier = contentModifier
                        )

                        detail == "local" -> LocalMusicScreen(
                            folderLabel = if (localFolder == LOCAL_ALL_TRACKS) "All tracks" else localFolder!!,
                            tracks = localShownTracks,
                            loading = state.localLoading,
                            onBack = { localFolder = null },
                            onRefresh = viewModel::onRefreshLocalWithMetadata,
                            onTrackClick = { track -> onRowClick(track, localShownTracks) },
                            onPlayAll = { viewModel.onPlayAll(localShownTracks) },
                            favoriteTrackIds = state.favoriteTrackIds,
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onAddToPlaylist = { addTarget = it },
                            selectedTrackIds = state.selectedTrackIds,
                            onToggleSelect = viewModel::onToggleSelect,
                            onClearSelection = viewModel::onClearSelection,
                            onSelectAll = viewModel::onSelectAll,
                            onAddSelectionToPlaylist = { addSelection = true },
                            serverTargets = browsableSources,
                            onUploadToServer = { baseUrl -> viewModel.onUploadPlaylistToServer(localShownTracks, baseUrl) },
                            onUploadTrackToServer = { track, baseUrl -> viewModel.onUploadPlaylistToServer(listOf(track), baseUrl) },
                            onDeleteTrack = { track -> viewModel.onDeleteLocalTrack(track) },

                            onDeleteAll = if (localFolder == LOCAL_ALL_TRACKS) {
                                { viewModel.onDeleteAllLocal() }
                            } else {
                                { viewModel.onDeleteLocalTracks(localShownTracks) }
                            },
                            deleteAllWhere = if (localFolder == LOCAL_ALL_TRACKS) "this device" else "this folder",
                            onPickVersion = { track -> viewModel.onPickVersion(track) },

                            onMove = if (localFolder == LOCAL_ALL_TRACKS) {
                                { from, to -> viewModel.onMoveLocalTrack(from, to) }
                            } else null,

                            showPlayAll = false,
                            showRefresh = false,
                            isOnServer = isOnServer,
                            currentTrackKey = nowKey,
                            playingProgress = nowProgress,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            onToggleShuffle = viewModel::onToggleShuffle,
                            onCycleRepeat = viewModel::onCycleRepeat,
                            modifier = contentModifier
                        )

                        detail == "mostplayed" -> PlaylistDetailScreen(
                            title = "Most Played",
                            tracks = state.mostPlayed,
                            favoriteTrackIds = state.favoriteTrackIds,
                            isFavoritesList = false,
                            onBack = { libraryDetail = null },
                            onTrackClick = { track -> onRowClick(track, state.mostPlayed) },
                            onPlayAll = { viewModel.onPlayAll(state.mostPlayed) },
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onAddToPlaylist = { addTarget = it },
                            selectedTrackIds = state.selectedTrackIds,
                            onToggleSelect = viewModel::onToggleSelect,
                            onClearSelection = viewModel::onClearSelection,
                            onSelectAll = viewModel::onSelectAll,
                            onAddSelectionToPlaylist = { addSelection = true },
                            onRemove = { viewModel.onRemoveFromMostPlayed(listOf(it)) },
                            removeText = "Remove from Most Played",
                            onRemoveSelection = { chosen ->
                                viewModel.onRemoveFromMostPlayed(chosen)
                                viewModel.onClearSelection()
                            },
                            onDelete = null,
                            serverTargets = browsableSources,
                            onSaveTrackToDevice = { track -> viewModel.onDownloadClicked(track) },
                            onUploadTrackToServer = { track, baseUrl -> viewModel.onUploadPlaylistToServer(listOf(track), baseUrl) },
                            onPickVersion = { track -> viewModel.onPickVersion(track) },
                            onRefresh = { viewModel.onRefreshTracksMetadata(state.mostPlayed) },
                            onRefreshAll = { viewModel.onRefreshTracksMetadata(state.mostPlayed, force = true) },
                            isOnDevice = isOnDevice,
                            isOnServer = isOnServer,
                            currentTrackKey = nowKey,
                            playingProgress = nowProgress,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            onToggleShuffle = viewModel::onToggleShuffle,
                            onCycleRepeat = viewModel::onCycleRepeat,
                            modifier = contentModifier
                        )

                        detail == "favorites" -> PlaylistDetailScreen(
                            title = "Liked Songs",
                            tracks = state.favorites,
                            favoriteTrackIds = state.favoriteTrackIds,
                            isFavoritesList = true,
                            onBack = { libraryDetail = null },
                            onTrackClick = { track -> onRowClick(track, state.favorites) },
                            onPlayAll = { viewModel.onPlayAll(state.favorites) },
                            onToggleFavorite = viewModel::onToggleFavorite,
                            onAddToPlaylist = { addTarget = it },
                            selectedTrackIds = state.selectedTrackIds,
                            onToggleSelect = viewModel::onToggleSelect,
                            onClearSelection = viewModel::onClearSelection,
                            onSelectAll = viewModel::onSelectAll,
                            onAddSelectionToPlaylist = { addSelection = true },
                            onRemove = viewModel::onToggleFavorite,
                            onRemoveSelection = { chosen ->

                                chosen.filter { it.id in state.favoriteTrackIds }
                                    .forEach(viewModel::onToggleFavorite)
                                viewModel.onClearSelection()
                            },
                            onDelete = null,
                            onMove = { from, to -> viewModel.onMoveFavorite(from, to) },
                            serverTargets = browsableSources,
                            onDownloadAll = { viewModel.onDownloadPlaylistToDevice(state.favorites, "Liked Songs") },
                            onUploadToServer = { baseUrl -> viewModel.onUploadPlaylistToServer(state.favorites, baseUrl, "Liked Songs") },
                            onSaveTrackToDevice = { track -> viewModel.onDownloadClicked(track) },
                            onUploadTrackToServer = { track, baseUrl -> viewModel.onUploadPlaylistToServer(listOf(track), baseUrl) },
                            onDeleteTrackFromServer = { track -> viewModel.onDeleteFromServer(track) },
                                        onDeleteTrackFromDevice = { track -> viewModel.onDeleteLocalCopy(track) },
                            onPickVersion = { track -> viewModel.onPickVersion(track) },
                            onRefresh = { viewModel.onRefreshTracksMetadata(state.favorites) },
                            onRefreshAll = { viewModel.onRefreshTracksMetadata(state.favorites, force = true) },
                            isOnDevice = isOnDevice,
                            isOnServer = isOnServer,
                            currentTrackKey = nowKey,
                            playingProgress = nowProgress,
                            shuffle = state.shuffle,
                            repeatMode = state.repeatMode,
                            onToggleShuffle = viewModel::onToggleShuffle,
                            onCycleRepeat = viewModel::onCycleRepeat,
                            modifier = contentModifier
                        )

                        else -> {
                            val playlist = state.playlists.firstOrNull { it.id == detail }
                            if (playlist == null) {
                                libraryDetail = null
                            } else {
                                PlaylistDetailScreen(
                                    title = playlist.name,
                                    tracks = playlist.tracks,
                                    favoriteTrackIds = state.favoriteTrackIds,
                                    isFavoritesList = false,
                                    onBack = { libraryDetail = null },
                                    onTrackClick = { track -> onRowClick(track, playlist.tracks) },
                                    onPlayAll = { viewModel.onPlayAll(playlist.tracks) },
                                    onToggleFavorite = viewModel::onToggleFavorite,
                                    onAddToPlaylist = { addTarget = it },
                                    selectedTrackIds = state.selectedTrackIds,
                                    onToggleSelect = viewModel::onToggleSelect,
                                    onClearSelection = viewModel::onClearSelection,
                                    onSelectAll = viewModel::onSelectAll,
                                    onAddSelectionToPlaylist = { addSelection = true },
                                    onRemove = { viewModel.onRemoveFromPlaylist(playlist.id, it.id) },
                                    onRemoveSelection = { chosen ->
                                        viewModel.onRemoveTracksFromPlaylist(playlist.id, chosen)
                                        viewModel.onClearSelection()
                                    },
                                    onDelete = {
                                        viewModel.onDeletePlaylist(playlist.id)
                                        libraryDetail = null
                                    },
                                    onRename = { viewModel.onRenamePlaylist(playlist.id, it) },
                                    onMove = { from, to -> viewModel.onMovePlaylistTrack(playlist.id, from, to) },

                                    exportJson = null,
                                    showPlayAll = false,
                                    serverTargets = browsableSources,
                                    onDownloadAll = { viewModel.onDownloadPlaylistToDevice(playlist.tracks, playlist.name) },
                                    onUploadToServer = { baseUrl -> viewModel.onUploadPlaylistToServer(playlist.tracks, baseUrl, playlist.name) },
                                    onSaveTrackToDevice = { track -> viewModel.onDownloadClicked(track) },
                                    onUploadTrackToServer = { track, baseUrl -> viewModel.onUploadPlaylistToServer(listOf(track), baseUrl) },
                                    onDeleteTrackFromServer = { track -> viewModel.onDeleteFromServer(track) },
                                        onDeleteTrackFromDevice = { track -> viewModel.onDeleteLocalCopy(track) },
                                    onPickVersion = { track -> viewModel.onPickVersion(track) },
                                    onRefresh = { viewModel.onRefreshTracksMetadata(playlist.tracks) },
                                    onRefreshAll = { viewModel.onRefreshTracksMetadata(playlist.tracks, force = true) },
                                    isOnDevice = isOnDevice,
                                    isOnServer = isOnServer,
                                    currentTrackKey = nowKey,
                                    playingProgress = nowProgress,
                                    shuffle = state.shuffle,
                                    repeatMode = state.repeatMode,
                                    onToggleShuffle = viewModel::onToggleShuffle,
                                    onCycleRepeat = viewModel::onCycleRepeat,
                                    modifier = contentModifier
                                )
                            }
                        }
                    }
                }

                Destination.Settings -> SettingsScreen(
                    extensionRepositories = extensionRepositories,
                    extensionSettingsFor = pluginManager::extensionSettings,
                    onSaveExtensionSettings = viewModel::onSaveExtensionSettings,
                    experimentalEnabled = state.experimentalPluginEnabled,
                    sources = state.sources,
                    backendTestStatus = state.backendTestStatus,
                    themes = state.themes,
                    selectedThemeId = state.selectedThemeId,
                    importStatus = state.importStatus,
                    playlistImportStatus = state.playlistImportStatus,
                    playlistImportPreview = state.playlistImportPreview,
                    onImportPlaylistLink = viewModel::onImportPlaylistLink,
                    onImportCsvPlaylist = { csv -> viewModel.onImportCsvPlaylist(csv) },
                    serverHost = state.serverHost,
                    servers = state.servers,
                    serverConfigStatus = state.serverConfigStatus,
                    onSaveServer = viewModel::onSaveServer,
                    onRemoveServer = viewModel::onRemoveServer,
                    onMoveServer = viewModel::onMoveServer,
                    appVersion = APP_VERSION,
                    updateStatus = state.updateError,
                    updateBusy = state.updateBusy,
                    onCheckForUpdates = { viewModel.onCheckForUpdates(manual = true) },
                    autoSync = state.autoSyncEnabled,
                    onToggleAutoSync = viewModel::onToggleAutoSync,
                    themeSync = state.themeSyncEnabled,
                    onToggleThemeSync = viewModel::onToggleThemeSync,
                    playbackSync = state.playbackSyncEnabled,
                    onTogglePlaybackSync = viewModel::onTogglePlaybackSync,
                    defaultServerDir = state.defaultServerDir,
                    serverFolders = state.serverFolders,
                    serverFoldersLoading = state.serverFoldersLoading,
                    onLoadServerFolders = viewModel::onLoadServerFolders,
                    onPickServerFolder = viewModel::onSetDefaultServerDir,
                    localFolderLabel = state.localFolderLabel,
                    localTrackCount = state.localTracks.size,
                    onPickLocalFolder = viewModel::onPickLocalFolder,
                    onRefreshLocal = viewModel::refreshLocalTracks,
                    onRetagDownloads = viewModel::onRetagLocalDownloads,
                    onConfirmPlaylistImport = viewModel::onConfirmPlaylistImport,
                    onCancelPlaylistImport = viewModel::onCancelPlaylistImport,
                    onExperimentalToggle = viewModel::onExperimentalPluginToggled,
                    highQualityArt = state.highQualityArt,
                    onHighQualityArtToggle = viewModel::onHighQualityArtToggled,
                    onToggleSource = viewModel::onToggleSource,
                    onInstallSource = viewModel::onInstallSource,
                    onUninstallSource = { id ->

                        val origin = pluginManager.extensionOrigin(id)
                        val name = state.sources.firstOrNull { it.id == id }?.name ?: "the extension"
                        if (origin != null && extensionRepositories != null) extensionRepositories.uninstall(origin, name)
                        else viewModel.onUninstallSource(id)
                    },
                    onRenameSource = viewModel::onRenameSource,
                    onUpdateSource = viewModel::onUpdateSource,
                    onTestBackend = viewModel::onTestBackend,
                    onSelectTheme = viewModel::onSelectTheme,
                    onInstallTheme = viewModel::onInstallTheme,
                    onUninstallTheme = viewModel::onUninstallTheme,
                    onImportPlugin = viewModel::onImportPlugin,
                    onExportPlugins = viewModel::exportPluginsJson,
                    sectionOrder = state.settingsSectionOrder,
                    locked = state.settingsLocked,
                    onReorder = viewModel::onReorderSettings,
                    onToggleLock = viewModel::onToggleSettingsLock,
                    modifier = contentModifier
                )

                Destination.Support -> SupportScreen(
                                        modifier = contentModifier
                )
            }
        }

        if (addSelection) {
            val pool = state.favorites + state.mostPlayed + state.browseTracks +
                state.localTracks + state.playlists.flatMap { it.tracks }
            val chosen = pool.distinctBy { it.id }.filter { it.id in state.selectedTrackIds }
            AddToPlaylistDialog(
                playlists = state.playlists,
                tracks = chosen,
                onDismiss = { addSelection = false },
                onAdd = { id, tracks -> viewModel.onAddTracksToPlaylist(id, tracks) },
                onRemove = { id, tracks -> viewModel.onRemoveTracksFromPlaylist(id, tracks) },
                onCreate = { name, tracks ->
                    viewModel.onCreatePlaylistWith(name, tracks)

                    viewModel.onClearSelection()
                    addSelection = false
                }
            )
        }

        addTarget?.let { target ->
            AddToPlaylistDialog(
                playlists = state.playlists,
                tracks = listOf(target),
                onDismiss = { addTarget = null },
                onAdd = { id, tracks -> viewModel.onAddTracksToPlaylist(id, tracks) },
                onRemove = { id, tracks -> viewModel.onRemoveTracksFromPlaylist(id, tracks) },
                onCreate = { name, tracks -> viewModel.onCreatePlaylistWith(name, tracks) }
            )
        }

        state.versionPickerFor?.let { original ->
            VersionPickerDialog(
                original = original,
                options = state.versionOptions,
                loading = state.versionLoading,
                playingKey = state.currentTrack?.matchKey(),
                isPlaying = state.playback == PlaybackState.PLAYING ||
                    state.playback == PlaybackState.BUFFERING,
                onPreview = { opt -> viewModel.onPreviewVersion(opt) },
                onStopPreview = viewModel::onPauseClicked,
                onPick = { chosen -> viewModel.onChooseVersion(chosen) },
                onUseLink = { url -> viewModel.onChooseVersionFromLink(url) },
                onPreviewLink = { url -> viewModel.onPreviewVersionFromLink(url) },
                onDismiss = viewModel::onDismissVersionPicker
            )
        }
      }
    }
}

@Composable
private fun VersionPickerDialog(
    original: Track,
    options: List<Track>,
    loading: Boolean,
    playingKey: String?,
    isPlaying: Boolean,
    onPreview: (Track) -> Unit,
    onStopPreview: () -> Unit,
    onPick: (Track) -> Unit,

    onUseLink: (String) -> Unit,

    onPreviewLink: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var linkText by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf("")
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Other versions") },
        text = {
            Column {
                Text(
                    "${original.title} - ${original.artist}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "▶ preview · Use = replace this song with that version",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(20.dp)
                        )
                        Text("  Finding versions…")
                    }
                    options.isEmpty() -> Text("No other versions found.")
                    else -> androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp)
                    ) {
                        items(options, key = { it.id }) { opt ->
                            val previewing = playingKey != null && opt.matchKey() == playingKey
                            androidx.compose.material3.ListItem(
                                leadingContent = {
                                    androidx.compose.material3.IconButton(
                                        onClick = {
                                            if (previewing && isPlaying) onStopPreview()
                                            else onPreview(opt)
                                        }
                                    ) {
                                        Icon(
                                            if (previewing && isPlaying)
                                                Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (previewing && isPlaying)
                                                "Pause preview" else "Preview",
                                            tint = if (previewing) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                headlineContent = {
                                    Text(opt.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        listOf(opt.artist, opt.durationLabel)
                                            .filter { it.isNotBlank() }.joinToString(" · "),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                },
                                trailingContent = {
                                    androidx.compose.material3.TextButton(onClick = { onPick(opt) }) {
                                        Text("Use")
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = linkText,
                        onValueChange = { linkText = it },
                        singleLine = true,
                        label = { Text("Or paste a link") },
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.IconButton(
                        onClick = { onPreviewLink(linkText.trim()) },
                        enabled = linkText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Preview link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            onUseLink(linkText.trim())
                            linkText = ""
                        },
                        enabled = linkText.isNotBlank()
                    ) { Text("Use") }
                }
            }
        }
    )
}

@Composable
private fun TransferBar(progress: Float?, label: String?, onCancel: () -> Unit) {
    if (progress == null) return

    val cancellable = progress < 1f
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label ?: "Transferring…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                if (cancellable) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            if (progress < 0f) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(end = 12.dp))
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaybackBar(
    title: String,
    thumbnailUrl: String,
    playback: PlaybackState,
    hasTrack: Boolean,
    hasNext: Boolean,
    hasPrevious: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val playing = playback == PlaybackState.PLAYING || playback == PlaybackState.BUFFERING
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpen() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrackThumbnail(thumbnailUrl, size = 40.dp)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = playback.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPrevious, enabled = hasTrack && hasPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            IconButton(onClick = { if (playing) onPause() else onPlay() }, enabled = hasTrack) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play"
                )
            }
            IconButton(onClick = onNext, enabled = hasTrack && hasNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }
    }
}
