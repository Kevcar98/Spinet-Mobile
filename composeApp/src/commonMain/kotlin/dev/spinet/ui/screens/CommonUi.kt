package dev.spinet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.spinet.model.SourcePlugin
import dev.spinet.model.Track
import dev.spinet.model.TrackSource
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItemColors

@Composable
fun cardListItemColors(): ListItemColors =
    ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

internal val LocalHighQualityArt = staticCompositionLocalOf { true }

private val WH_SIZE = Regex("=w\\d+-h\\d+")
private val S_SIZE = Regex("=s\\d+")

@Composable
internal fun TrackThumbnail(
    thumbnailUrl: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val placeholder = @Composable {
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(size / 2),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (thumbnailUrl.isBlank()) {
            placeholder()
        } else {
            val model = if (LocalHighQualityArt.current) thumbnailUrl
            else thumbnailUrl
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = { placeholder() },
                loading = { placeholder() },
                success = { SubcomposeAsyncImageContent() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = track.artist,
    isFavorite: Boolean = false,
    alsoOnDevice: Boolean = false,
    alsoOnServer: Boolean = false,
    serverTargets: List<SourcePlugin> = emptyList(),
    onToggleFavorite: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onSaveToDevice: (() -> Unit)? = null,
    onUploadToServer: ((String) -> Unit)? = null,
    onSaveToServer: ((String) -> Unit)? = null,
    onDeleteFromDevice: (() -> Unit)? = null,
    onDeleteFromServer: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onPickVersion: (() -> Unit)? = null,
    removeIsUnlike: Boolean = false,
    isCurrent: Boolean = false,
    progress: Float = 0f,

    selected: Boolean = false,
    selectionActive: Boolean = false,
    onToggleSelect: (() -> Unit)? = null,
    leadingHandle: (@Composable () -> Unit)? = null,
    trailingExtras: (@Composable RowScope.() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isLocal = track.source == TrackSource.LOCAL

    val onServer = track.serverBaseUrl.isNotBlank() || alsoOnServer

    val highlight = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified

    val selectable = onToggleSelect != null
    Column {
    ListItem(
        modifier = modifier.combinedClickable(
            onClick = {
                if (selectionActive && selectable) onToggleSelect!!() else onClick()
            },

            onLongClick = { menuOpen = true }
        ),
        colors = when {
            selected -> ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
            isCurrent -> ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
            else -> ListItemDefaults.colors()
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leadingHandle?.invoke()

                if (selectionActive && selectable) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelect!!() },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                TrackThumbnail(track.thumbnailUrl)
            }
        },
        headlineContent = {
            Text(
                track.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = highlight,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null
            )
        },
        supportingContent = {
            Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingExtras?.invoke(this)

                val markers = buildList {
                    if (onServer) add(Icons.Filled.Cloud to "On your server")

                    if (isLocal || alsoOnDevice) add(Icons.Filled.OfflinePin to "On this device")
                }
                markers.forEach { (icon, desc) ->
                    Icon(
                        icon,
                        contentDescription = desc,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp).size(18.dp)
                    )
                }
                if (onToggleFavorite != null) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (selectable && !selectionActive) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Checklist, null) },
                            text = { Text("Select") },
                            onClick = { menuOpen = false; onToggleSelect!!() }
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.PlaylistAdd, null) },
                            text = { Text("Add to playlist") },
                            onClick = { menuOpen = false; onAddToPlaylist() }
                        )
                    }
                    if (onPickVersion != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            text = { Text("Other versions") },
                            onClick = { menuOpen = false; onPickVersion() }
                        )
                    }

                    if (!isLocal && onSaveToDevice != null && track.downloadable) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.Download, null) },
                            text = { Text("Save to this device") },
                            onClick = { menuOpen = false; onSaveToDevice() }
                        )
                    }

                    if (isLocal && onUploadToServer != null) {
                        serverTargets.forEach { target ->
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.CloudUpload, null) },
                                text = { Text("Upload to ${target.name}") },
                                onClick = { menuOpen = false; onUploadToServer(target.baseUrl) }
                            )
                        }
                    }

                    if (!isLocal && !onServer && onSaveToServer != null && track.downloadable) {
                        serverTargets.forEach { target ->
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.CloudUpload, null) },
                                text = { Text("Save to ${target.name}") },
                                onClick = { menuOpen = false; onSaveToServer(target.baseUrl) }
                            )
                        }
                    }

                    if ((isLocal || alsoOnDevice) && onDeleteFromDevice != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.DeleteForever, null) },
                            text = { Text("Delete from device") },
                            onClick = { menuOpen = false; onDeleteFromDevice() }
                        )
                    }
                    if (onServer && onDeleteFromServer != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.DeleteForever, null) },
                            text = { Text("Delete from server") },
                            onClick = { menuOpen = false; onDeleteFromServer() }
                        )
                    }
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            leadingIcon = { Icon(Icons.Filled.RemoveCircleOutline, null) },
                            text = { Text(if (removeIsUnlike) "Unlike" else "Remove from playlist") },
                            onClick = { menuOpen = false; onRemoveFromPlaylist() }
                        )
                    }
                }
            }
        }
    )
    if (isCurrent) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
    }
}

@Composable
internal fun ConfirmDeleteAllDialog(
    count: Int,
    where: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete all music?") },
        text = {
            Text("This permanently deletes $count song(s) from $where. This can't be undone.")
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("Delete all", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun Pill(
    icon: ImageVector?,
    text: String,
    container: Color,
    content: Color
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

enum class SortMode(val label: String) {
    CUSTOM("Custom order"),
    TITLE_AZ("Title A–Z"),
    TITLE_ZA("Title Z–A"),
    ARTIST_AZ("Artist A–Z"),
    ARTIST_ZA("Artist Z–A"),
    NEWEST("Newest first"),
    OLDEST("Oldest first")
}

fun sortTracks(tracks: List<Track>, mode: SortMode): List<Track> = when (mode) {
    SortMode.CUSTOM -> tracks
    SortMode.TITLE_AZ -> tracks.sortedBy { it.title.trim().lowercase() }
    SortMode.TITLE_ZA -> tracks.sortedByDescending { it.title.trim().lowercase() }
    SortMode.ARTIST_AZ -> tracks.sortedWith(
        compareBy({ it.artist.trim().lowercase() }, { it.title.trim().lowercase() })
    )
    SortMode.ARTIST_ZA -> tracks.sortedWith(
        compareByDescending<Track> { it.artist.trim().lowercase() }.thenBy { it.title.trim().lowercase() }
    )
    SortMode.NEWEST -> tracks.sortedByDescending { it.modifiedAt ?: Long.MIN_VALUE }
    SortMode.OLDEST -> tracks.sortedBy { it.modifiedAt ?: Long.MAX_VALUE }
}

@Composable
internal fun SortButton(current: SortMode, onSelect: (SortMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.Filled.Sort,
                contentDescription = "Sort",
                tint = if (current != SortMode.CUSTOM) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.label,
                            color = if (mode == current) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { open = false; onSelect(mode) }
                )
            }
        }
    }
}

@Composable
internal fun FastScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val total by remember { androidx.compose.runtime.derivedStateOf { listState.layoutInfo.totalItemsCount } }
    if (total <= 30) return

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var trackHeight by remember { mutableStateOf(0) }
    val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 56.dp.toPx() }

    val fraction by remember {
        androidx.compose.runtime.derivedStateOf {
            val li = listState.layoutInfo
            val denom = (li.totalItemsCount - li.visibleItemsInfo.size).coerceAtLeast(1)
            (listState.firstVisibleItemIndex.toFloat() / denom).coerceIn(0f, 1f)
        }
    }

    fun jumpTo(y: Float) {
        if (trackHeight <= 0) return
        val f = ((y - thumbHeightPx / 2) / (trackHeight - thumbHeightPx)).coerceIn(0f, 1f)
        val li = listState.layoutInfo
        val target = (f * (li.totalItemsCount - li.visibleItemsInfo.size).coerceAtLeast(0)).toInt()
        scope.launch { listState.scrollToItem(target) }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(28.dp)
            .onSizeChanged { trackHeight = it.height }
            .pointerInput(Unit) {
                detectTapGestures { offset -> jumpTo(offset.y) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    jumpTo(change.position.y)
                }
            }
    ) {
        val offsetY = fraction * (trackHeight - thumbHeightPx).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { androidx.compose.ui.unit.IntOffset(0, offsetY.toInt()) }
                .width(6.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
        )
    }
}
