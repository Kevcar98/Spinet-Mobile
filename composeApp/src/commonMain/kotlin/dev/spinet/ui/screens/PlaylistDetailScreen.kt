package dev.spinet.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spinet.model.SourcePlugin
import dev.spinet.model.Track
import dev.spinet.model.matchKey
import dev.spinet.ui.SaveJsonFileButton
import dev.spinet.viewmodel.RepeatMode
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.DisposableEffect

@Composable
fun PlaylistDetailScreen(
    title: String,
    tracks: List<Track>,
    favoriteTrackIds: Set<String>,
    isFavoritesList: Boolean,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: ((Track) -> Unit)? = null,

    selectedTrackIds: Set<String> = emptySet(),
    onToggleSelect: ((Track) -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onSelectAll: ((List<Track>) -> Unit)? = null,
    onAddSelectionToPlaylist: (() -> Unit)? = null,

    onRemoveSelection: ((List<Track>) -> Unit)? = null,
    onRemove: ((Track) -> Unit)?,
    onDelete: (() -> Unit)?,
    onRename: ((String) -> Unit)? = null,
    onMove: ((fromIndex: Int, toIndex: Int) -> Unit)? = null,
    exportJson: (() -> String)? = null,
    serverTargets: List<SourcePlugin> = emptyList(),
    onDownloadAll: (() -> Unit)? = null,
    onUploadToServer: ((String) -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null,
    onSaveTrackToDevice: ((Track) -> Unit)? = null,
    onUploadTrackToServer: ((Track, String) -> Unit)? = null,
    onDeleteTrackFromServer: ((Track) -> Unit)? = null,
    onDeleteTrackFromDevice: ((Track) -> Unit)? = null,
    onPickVersion: ((Track) -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,

    onRefreshAll: (() -> Unit)? = null,
    isOnDevice: (Track) -> Boolean = { false },
    isOnServer: (Track) -> Boolean = { it.serverBaseUrl.isNotBlank() },
    currentTrackKey: String? = null,
    playingProgress: Float = 0f,
    shuffle: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onToggleShuffle: (() -> Unit)? = null,
    onCycleRepeat: (() -> Unit)? = null,

    showPlayAll: Boolean = true,

    onPlayAll: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var editing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(title) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    val reorderable = onMove != null
    var reorderMode by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(SortMode.CUSTOM) }
    var confirmRemove by remember { mutableStateOf(false) }

    var filter by remember { mutableStateOf("") }
    val shownTracks = remember(tracks, sortMode, filter) {
        val sorted = sortTracks(tracks, sortMode)
        val needle = filter.trim()
        if (needle.isBlank()) sorted else sorted.filter {
            it.title.contains(needle, ignoreCase = true) ||
                it.artist.contains(needle, ignoreCase = true)
        }
    }

    if (onClearSelection != null) {
        DisposableEffect(title) {
            onDispose { onClearSelection() }
        }
    }

    val reorderState = rememberReorderState()

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            if (editing && onRename != null) {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    onRename(editName)
                    editing = false
                }) {
                    Icon(Icons.Filled.Check, contentDescription = "Save name")
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (showPlayAll && shownTracks.isNotEmpty()) {
                    FilledIconButton(onClick = {
                        onPlayAll?.invoke() ?: onTrackClick(shownTracks.first())
                    }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play all")
                    }
                }
                if (exportJson != null) {
                    SaveJsonFileButton(
                        label = "Export",
                        suggestedName = "$title.json",
                        content = exportJson
                    )
                }
                if (onRefresh != null) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
                SortButton(sortMode) { sortMode = it }
                if (onToggleShuffle != null) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                            tint = if (shuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        )
                    }
                }
                if (onCycleRepeat != null) {
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatMode == RepeatMode.OFF)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (reorderable || onRename != null || onDelete != null ||
                    onDownloadAll != null || onUploadToServer != null || onDeleteAll != null) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text("Rename playlist") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    editName = title
                                    editing = true
                                }
                            )
                        }
                        if (reorderable) {
                            DropdownMenuItem(
                                text = { Text(if (reorderMode) "Done reordering" else "Reorder tracks") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.SwapVert,
                                        contentDescription = null,
                                        tint = if (reorderMode && sortMode == SortMode.CUSTOM) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                enabled = sortMode == SortMode.CUSTOM,
                                onClick = { menuOpen = false; reorderMode = !reorderMode }
                            )
                        }
                        if (onDownloadAll != null) {
                            DropdownMenuItem(
                                text = { Text("Download all to device") },
                                onClick = { menuOpen = false; onDownloadAll() }
                            )
                            if (onRefreshAll != null) {
                                DropdownMenuItem(
                                    text = { Text("Re-fetch all info & cover art") },
                                    onClick = { menuOpen = false; onRefreshAll() }
                                )
                            }
                        }
                        if (onUploadToServer != null) {
                            serverTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text("Upload all to ${target.name}") },
                                    onClick = { menuOpen = false; onUploadToServer(target.baseUrl) }
                                )
                            }
                        }
                        if (onDeleteAll != null) {
                            DropdownMenuItem(
                                text = { Text("Delete all from server") },
                                onClick = { menuOpen = false; confirmDeleteAll = true }
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("Delete playlist") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; onDelete() }
                            )
                        }
                    }
                }
            }
        }

        if (confirmDeleteAll && onDeleteAll != null) {
            ConfirmDeleteAllDialog(
                count = tracks.count { it.serverBaseUrl.isNotBlank() },
                where = "the server",
                onConfirm = { confirmDeleteAll = false; onDeleteAll() },
                onDismiss = { confirmDeleteAll = false }
            )
        }

        if (tracks.isEmpty()) {
            Text(
                text = "No tracks yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (confirmRemove && onRemoveSelection != null) {
            val chosen = shownTracks.filter { it.id in selectedTrackIds }
            val verb = if (isFavoritesList) "Unlike" else "Remove"
            AlertDialog(
                onDismissRequest = { confirmRemove = false },
                title = { Text("Are you sure?") },
                text = {
                    val where = if (isFavoritesList) "Liked Songs" else "\"$title\""
                    val listed = chosen.take(MAX_NAMED_IN_CONFIRM)
                    val rest = chosen.size - listed.size
                    Column {
                        Text(
                            if (chosen.size == 1) {
                                "${verb.lowercase().replaceFirstChar { it.uppercase() }} " +
                                    "\"${chosen.first().title}\" from $where?"
                            } else {
                                "${verb.lowercase().replaceFirstChar { it.uppercase() }} " +
                                    "these ${chosen.size} songs from $where?"
                            }
                        )
                        if (chosen.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            listed.forEach { song ->
                                Text(
                                    "•  ${song.title}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (rest > 0) {
                                Text(
                                    "•  and $rest more",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRemove = false
                            onRemoveSelection(chosen)
                        }
                    ) {
                        Text(verb, color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
                }
            )
        }

        if (onToggleSelect != null) {
            SelectionBar(
                count = selectedTrackIds.count { id -> shownTracks.any { it.id == id } },
                onClear = { onClearSelection?.invoke() },
                onSelectAll = { onSelectAll?.invoke(shownTracks) },
                onAddToPlaylist = { onAddSelectionToPlaylist?.invoke() },

                onRemove = onRemoveSelection?.let { { confirmRemove = true } },
                removeLabel = if (isFavoritesList) "Unlike" else "Remove"
            )
        }

        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        if (tracks.size >= 10) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.isNotBlank()) {
                        IconButton(onClick = { filter = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Filter these tracks") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }

        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),

            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(shownTracks, key = { _, track -> track.id }) { index, track ->
                val isCurrent = currentTrackKey != null && track.matchKey() == currentTrackKey
                TrackRow(
                    modifier = Modifier.reorderableItem(reorderState, index),
                    track = track,
                    onClick = { onTrackClick(track) },
                    isFavorite = track.id in favoriteTrackIds,
                    alsoOnDevice = isOnDevice(track),
                    alsoOnServer = isOnServer(track),
                    isCurrent = isCurrent,
                    progress = if (isCurrent) playingProgress else 0f,
                    serverTargets = serverTargets,
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToPlaylist = onAddToPlaylist?.let { cb -> { cb(track) } },
                    selected = track.id in selectedTrackIds,
                    selectionActive = selectedTrackIds.isNotEmpty(),
                    onToggleSelect = onToggleSelect?.let { cb -> { cb(track) } },
                    onSaveToDevice = onSaveTrackToDevice?.let { cb -> { cb(track) } },
                    onUploadToServer = onUploadTrackToServer?.let { cb -> { baseUrl -> cb(track, baseUrl) } },

                    onSaveToServer = onUploadTrackToServer?.let { cb -> { baseUrl -> cb(track, baseUrl) } },
                    onDeleteFromServer = onDeleteTrackFromServer?.let { cb -> { cb(track) } },
                    onDeleteFromDevice = onDeleteTrackFromDevice?.let { cb -> { cb(track) } },
                    onPickVersion = onPickVersion?.let { cb -> { cb(track) } },

                    onRemoveFromPlaylist = if (isFavoritesList) null
                    else onRemove?.let { cb -> { cb(track) } },
                    removeIsUnlike = isFavoritesList,

                    leadingHandle = if (reorderable && reorderMode && sortMode == SortMode.CUSTOM) {
                        {
                            DragHandle(
                                Modifier.reorderHandle(
                                    state = reorderState,
                                    index = index,
                                    itemCount = shownTracks.size,
                                    key = track.id,
                                    onMove = { from, to -> onMove?.invoke(from, to) }
                                )
                            )
                        }
                    } else null
                )
            }
        }
        FastScrollbar(listState, Modifier.align(Alignment.CenterEnd))
        }
    }
}

private const val MAX_NAMED_IN_CONFIRM = 6
