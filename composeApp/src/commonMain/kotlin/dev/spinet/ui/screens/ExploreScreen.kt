package dev.spinet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spinet.data.ExploreFolder
import dev.spinet.data.ExplorePage
import dev.spinet.data.ExploreShelf
import dev.spinet.data.PlaylistHit
import dev.spinet.data.ExploreSource
import dev.spinet.extensions.ExtensionSettingsUi
import dev.spinet.model.SourcePlugin
import dev.spinet.model.Track
import kotlinx.coroutines.CancellationException

internal data class ExploreCrumb(
    val source: ExploreSource,
    val folderId: String?,
    val title: String,
    val artworkUrl: String = "",
    val foldersOnly: Boolean = false
)

internal class ExploreNavState {
    var stack by mutableStateOf(emptyList<ExploreCrumb>())

    val canPop: Boolean get() = stack.isNotEmpty()

    fun pop() {
        stack = stack.dropLast(1)
    }

    fun openFolder(source: ExploreSource, folder: ExploreFolder) {
        stack = listOf(ExploreCrumb(source, folder.id, folder.name, folder.artworkUrl))
    }
}

@Composable
internal fun rememberExploreNav(): ExploreNavState = remember { ExploreNavState() }

internal val LocalShowRowArrows = staticCompositionLocalOf { true }

@Composable
internal fun ExploreScreen(
    nav: ExploreNavState,
    sources: List<ExploreSource>,
    loadForYou: suspend () -> List<ExploreShelf>,
    loadPage: suspend (pluginId: String, parentId: String?) -> ExplorePage,
    foldersAsGrid: Boolean,
    onToggleFolderLayout: () -> Unit,
    sectionOrder: List<String>,
    onReorderSections: (List<String>) -> Unit,
    folderSettingsFor: (pluginId: String, folderId: String) -> ExtensionSettingsUi?,
    onSaveFolderSettings: (pluginId: String, values: Map<String, String>) -> Unit,
    serverTargets: List<SourcePlugin>,
    onSaveToLibrary: (name: String, tracks: List<Track>, imageUrl: String, pluginId: String, folderId: String) -> Unit,
    onDownloadAll: (name: String, tracks: List<Track>) -> Unit,
    onUploadAll: (name: String, tracks: List<Track>, serverBaseUrl: String) -> Unit,
    favoriteTrackIds: Set<String>,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPickVersion: ((Track) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val crumb = nav.stack.lastOrNull()

    if (crumb == null) {
        ExploreHome(
            sources = sources,
            loadForYou = loadForYou,
            loadPage = loadPage,
            onOpenFolder = { source, folder -> nav.openFolder(source, folder) },
            onOpenSource = { nav.stack = listOf(ExploreCrumb(it, null, it.name)) },
            sectionOrder = sectionOrder,
            onReorderSections = onReorderSections,
            onPlay = onPlay,
            modifier = modifier
        )
    } else {
        ExploreFolderScreen(
            crumb = crumb,
            loadPage = loadPage,
            folderSettingsFor = folderSettingsFor,
            onSaveFolderSettings = onSaveFolderSettings,
            serverTargets = serverTargets,
            onSaveToLibrary = onSaveToLibrary,
            onDownloadAll = onDownloadAll,
            onUploadAll = onUploadAll,
            favoriteTrackIds = favoriteTrackIds,
            onBack = { nav.pop() },
            onOpenFolder = { folder -> nav.stack = nav.stack + ExploreCrumb(crumb.source, folder.id, folder.name, folder.artworkUrl) },
            onSeeAllFolders = { nav.stack = nav.stack + crumb.copy(title = "${crumb.title}: playlists", foldersOnly = true) },
            foldersAsGrid = foldersAsGrid,
            onToggleFolderLayout = onToggleFolderLayout,
            onPlay = onPlay,
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = onAddToPlaylist,
            onPickVersion = onPickVersion,
            modifier = modifier
        )
    }
}

@Composable
private fun ExploreHome(
    sources: List<ExploreSource>,
    loadForYou: suspend () -> List<ExploreShelf>,
    loadPage: suspend (pluginId: String, parentId: String?) -> ExplorePage,
    onOpenFolder: (ExploreSource, ExploreFolder) -> Unit,
    onOpenSource: (ExploreSource) -> Unit,
    sectionOrder: List<String>,
    onReorderSections: (List<String>) -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    modifier: Modifier
) {
    var refresh by remember { mutableStateOf(0) }
    var shelves by remember { mutableStateOf<List<ExploreShelf>?>(null) }
    LaunchedEffect(refresh) {
        shelves = null
        shelves = try {
            loadForYou()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    var roots by remember(sources) { mutableStateOf<Map<String, List<ExploreFolder>>>(emptyMap()) }
    LaunchedEffect(sources) {
        sources.forEach { source ->
            val folders = try {
                loadPage(source.pluginId, null).folders
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            roots = roots + (source.pluginId to folders)
        }
    }

    val sectionKeys = sources.map { it.pluginId } + FOR_YOU_SECTION
    val ordered = (sectionOrder.filter { it in sectionKeys } + sectionKeys).distinct()
    var reordering by remember { mutableStateOf(false) }
    if (reordering) {
        ReorderSectionsDialog(
            keys = ordered,
            nameOf = { key -> sources.firstOrNull { it.pluginId == key }?.name ?: "Recommended for you" },
            onDismiss = { reordering = false },
            onSave = { onReorderSections(it); reordering = false }
        )
    }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        if (sources.isEmpty()) {
            item {
                Box(Modifier.padding(16.dp)) {
                    HintText("Switch on an extension that can browse, in Settings > Sources, to find charts, genres and new music here.")
                }
            }
        }
        ordered.forEachIndexed { index, key ->
            val top = if (index == 0) 8.dp else 24.dp
            val reorder: (@Composable () -> Unit)? = if (index == 0 && ordered.size > 1) {
                {
                    IconButton(onClick = { reordering = true }) {
                        Icon(Icons.Filled.SwapVert, contentDescription = "Change the order of these sections")
                    }
                }
            } else null
            if (key == FOR_YOU_SECTION) {
                item(key = "foryou_title") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = top, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recommended for you",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { refresh++ }, enabled = shelves != null) {
                            Icon(Icons.Filled.Refresh, contentDescription = "New picks")
                        }
                        reorder?.invoke()
                    }
                }
                val loaded = shelves
                when {
                    loaded == null -> item(key = "foryou_loading") { LoadingRow() }
                    loaded.isEmpty() -> item(key = "foryou_empty") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            HintText("Play some music first. Picks here come from what you listen to, your playlists and your likes.")
                        }
                    }
                    else -> items(loaded, key = { "shelf_${it.title}" }) { shelf ->
                        ShelfRow(shelf = shelf, onPlay = onPlay)
                    }
                }
            } else {
                val source = sources.first { it.pluginId == key }
                item(key = "source_${source.pluginId}") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = top, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            source.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        reorder?.invoke()
                    }
                }
                val folders = roots[source.pluginId]
                when {
                    folders == null -> item(key = "loading_${source.pluginId}") { LoadingRow() }
                    folders.isEmpty() -> item(key = "open_${source.pluginId}") {
                        ListItem(
                            headlineContent = { Text("Browse ${source.name}") },
                            leadingContent = { Icon(Icons.Filled.Explore, contentDescription = null) },
                            modifier = Modifier.clickable { onOpenSource(source) }
                        )
                    }
                    else -> items(folders, key = { "root_${source.pluginId}_${it.id}" }) { folder ->
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            leadingContent = { ItemArt(folder.artworkUrl, size = 40.dp) },
                            modifier = Modifier.clickable { onOpenFolder(source, folder) }
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
}

@Composable
private fun ReorderSectionsDialog(
    keys: List<String>,
    nameOf: (String) -> String,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var order by remember(keys) { mutableStateOf(keys) }
    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Order of sections") },
        text = {
            Column {
                order.forEachIndexed { index, key ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nameOf(key), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { move(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(onClick = { move(index, index + 1) }, enabled = index < order.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(order) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ShelfRow(shelf: ExploreShelf, onPlay: (Track, List<Track>) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                shelf.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { onPlay(shelf.tracks.first(), shelf.tracks) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Play")
            }
        }
        ArrowRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(shelf.tracks, key = { "${shelf.title}_${it.id}" }) { track ->
                Column(
                    modifier = Modifier
                        .width(SHELF_CARD)
                        .clickable { onPlay(track, shelf.tracks) }
                ) {
                    TrackThumbnail(track.thumbnailUrl, size = SHELF_CARD)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private val SHELF_CARD = 136.dp

private const val FOR_YOU_SECTION = "for_you"

private val BANNER_ART = 168.dp

private val GRID_GAP = 12.dp

@Composable
private fun ExploreFolderScreen(
    crumb: ExploreCrumb,
    loadPage: suspend (pluginId: String, parentId: String?) -> ExplorePage,
    folderSettingsFor: (pluginId: String, folderId: String) -> ExtensionSettingsUi?,
    onSaveFolderSettings: (pluginId: String, values: Map<String, String>) -> Unit,
    serverTargets: List<SourcePlugin>,
    onSaveToLibrary: (name: String, tracks: List<Track>, imageUrl: String, pluginId: String, folderId: String) -> Unit,
    onDownloadAll: (name: String, tracks: List<Track>) -> Unit,
    onUploadAll: (name: String, tracks: List<Track>, serverBaseUrl: String) -> Unit,
    favoriteTrackIds: Set<String>,
    onBack: () -> Unit,
    onOpenFolder: (ExploreFolder) -> Unit,
    onSeeAllFolders: () -> Unit,
    foldersAsGrid: Boolean,
    onToggleFolderLayout: () -> Unit,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPickVersion: ((Track) -> Unit)?,
    modifier: Modifier
) {
    var reload by remember(crumb) { mutableStateOf(0) }
    val bannerArt = crumb.artworkUrl.takeIf { it.startsWith("http") && !crumb.foldersOnly }.orEmpty()
    var page by remember(crumb) { mutableStateOf<ExplorePage?>(null) }
    var error by remember(crumb) { mutableStateOf<String?>(null) }
    val gridEligible = crumb.foldersOnly || page?.let { p ->
        p.tracks.isEmpty() && p.folders.isNotEmpty() && p.folders.all { it.artworkUrl.startsWith("http") }
    } == true
    val folderSettings = remember(crumb, reload) {
        crumb.folderId?.takeUnless { crumb.foldersOnly }?.let { folderSettingsFor(crumb.source.pluginId, it) }
    }
    var showSettings by remember(crumb) { mutableStateOf(false) }
    if (showSettings && folderSettings != null) {
        ExtensionSettingsDialog(
            title = crumb.title,
            settings = folderSettings,
            onDismiss = { showSettings = false },
            onSave = { values ->
                onSaveFolderSettings(crumb.source.pluginId, values)
                showSettings = false
                reload++
            }
        )
    }
    LaunchedEffect(crumb, reload) {
        error = null
        try {
            page = loadPage(crumb.source.pluginId, crumb.folderId).let {
                if (crumb.foldersOnly) it.copy(tracks = emptyList()) else it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Couldn't load this."
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    val gridColumns = ((maxWidth - 32.dp + GRID_GAP) / (SHELF_CARD + GRID_GAP)).toInt().coerceAtLeast(2)
    val gridCell = (maxWidth - 32.dp - GRID_GAP * (gridColumns - 1)) / gridColumns
    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    crumb.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val tracks = page?.tracks.orEmpty()
                if (tracks.isNotEmpty()) {
                    FilledTonalButton(onClick = { onPlay(tracks.first(), tracks) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play all")
                    }
                }
                if (tracks.isNotEmpty()) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More for ${crumb.title}")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Save to my library") },
                                leadingIcon = { Icon(Icons.Filled.LibraryAdd, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onSaveToLibrary(crumb.title, tracks, bannerArt, crumb.source.pluginId, crumb.folderId.orEmpty())
                                }
                            )
                            if (tracks.any { it.downloadable }) {
                                DropdownMenuItem(
                                    text = { Text("Save all to device") },
                                    leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                    onClick = { menuOpen = false; onDownloadAll(crumb.title, tracks) }
                                )
                            }
                            serverTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text("Upload all to ${target.name}") },
                                    leadingIcon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                                    onClick = { menuOpen = false; onUploadAll(crumb.title, tracks, target.baseUrl) }
                                )
                            }
                        }
                    }
                }
                if (gridEligible) {
                    IconButton(onClick = onToggleFolderLayout) {
                        Icon(
                            if (foldersAsGrid) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = if (foldersAsGrid) "Show as list" else "Show as grid"
                        )
                    }
                }
                if (folderSettings != null) {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings for ${crumb.title}")
                    }
                }
            }
        }
        val loaded = page
        if (bannerArt.isNotEmpty() && loaded != null && loaded.tracks.isNotEmpty()) {
            item(key = "banner") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TrackThumbnail(bannerArt, size = BANNER_ART)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            crumb.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${loaded.tracks.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        when {
            error != null -> item { HintText(error!!) }
            loaded == null -> item { LoadingRow() }
            loaded.folders.isEmpty() && loaded.tracks.isEmpty() -> item { HintText("Nothing here yet.") }
            loaded.folders.isNotEmpty() && loaded.tracks.isNotEmpty() -> {
                item(key = "playlists_title") { SectionTitle("Playlists", action = "See all", onAction = onSeeAllFolders) }
                item(key = "playlists_row") {
                    ArrowRow {
                        items(loaded.folders, key = { "card_${it.id}" }) { folder ->
                            Column(
                                modifier = Modifier
                                    .width(SHELF_CARD)
                                    .clickable { onOpenFolder(folder) }
                            ) {
                                ItemArt(folder.artworkUrl, size = SHELF_CARD)
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                item(key = "songs_title") { SectionTitle("Songs") }
                items(loaded.tracks, key = { "track_${it.id}" }) { track ->
                    ExploreTrackRow(track, loaded.tracks, favoriteTrackIds, onPlay, onToggleFavorite, onAddToPlaylist, onPickVersion)
                }
            }
            gridEligible && foldersAsGrid -> {
                items(loaded.folders.chunked(gridColumns), key = { row -> "grid_${row.first().id}" }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(GRID_GAP)
                    ) {
                        row.forEach { folder ->
                            Column(
                                modifier = Modifier
                                    .width(gridCell)
                                    .clickable { onOpenFolder(folder) }
                            ) {
                                ItemArt(folder.artworkUrl, size = gridCell)
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    folder.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                items(loaded.folders, key = { "folder_${it.id}" }) { folder ->
                    ListItem(
                        headlineContent = { Text(folder.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { ItemArt(folder.artworkUrl, size = 48.dp) },
                        modifier = Modifier.clickable { onOpenFolder(folder) }
                    )
                }
                items(loaded.tracks, key = { "track_${it.id}" }) { track ->
                    ExploreTrackRow(track, loaded.tracks, favoriteTrackIds, onPlay, onToggleFavorite, onAddToPlaylist, onPickVersion)
                }
            }
        }
        item { Spacer(Modifier.size(24.dp)) }
    }
    }
}

@Composable
private fun ExploreTrackRow(
    track: Track,
    list: List<Track>,
    favoriteTrackIds: Set<String>,
    onPlay: (Track, List<Track>) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPickVersion: ((Track) -> Unit)?
) {
    TrackRow(
        track = track,
        onClick = { onPlay(track, list) },
        isFavorite = track.id in favoriteTrackIds,
        onToggleFavorite = { onToggleFavorite(track) },
        onAddToPlaylist = { onAddToPlaylist(track) },
        onPickVersion = onPickVersion?.let { pick -> { pick(track) } }
    )
}

@Composable
private fun ArrowRow(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val arrows = LocalShowRowArrows.current
    val step = with(LocalDensity.current) { (SHELF_CARD * 3).toPx() }
    Box(Modifier.fillMaxWidth()) {
        LazyRow(
            state = state,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        if (arrows && state.canScrollBackward) {
            FilledTonalIconButton(
                onClick = { scope.launch { state.animateScrollBy(-step) } },
                modifier = Modifier.align(Alignment.TopStart).padding(start = 4.dp, top = SHELF_CARD / 2 - 20.dp)
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Scroll left")
            }
        }
        if (arrows && state.canScrollForward) {
            FilledTonalIconButton(
                onClick = { scope.launch { state.animateScrollBy(step) } },
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 4.dp, top = SHELF_CARD / 2 - 20.dp)
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Scroll right")
            }
        }
    }
}

@Composable
internal fun PlaylistResultsRow(hits: List<PlaylistHit>, onOpen: (PlaylistHit) -> Unit) {
    if (hits.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionTitle("Playlists")
        ArrowRow {
            items(hits, key = { "hit_${it.source.pluginId}_${it.folder.id}" }) { hit ->
                Column(
                    modifier = Modifier
                        .width(SHELF_CARD)
                        .clickable { onOpen(hit) }
                ) {
                    ItemArt(hit.folder.artworkUrl, size = SHELF_CARD)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        hit.folder.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        SectionTitle("Songs")
    }
}

@Composable
private fun SectionTitle(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}
