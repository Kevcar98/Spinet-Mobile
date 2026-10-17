package dev.spinet.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dev.spinet.ui.components.ThinSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import dev.spinet.audio.PlaybackState
import dev.spinet.model.SourcePlugin
import dev.spinet.model.Track
import dev.spinet.viewmodel.RepeatMode
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight

@Composable
fun NowPlayingScreen(
    track: Track?,
    playback: PlaybackState,
    isFavorite: Boolean,
    positionMs: Long,
    durationMs: Long,
    hasNext: Boolean = false,
    hasPrevious: Boolean = false,

    hasPreviousTrack: Boolean = false,
    shuffle: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},

    onPreviousTrack: () -> Unit = onPrevious,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    canDownload: Boolean = false,
    onDownload: () -> Unit = {},
    alreadyOnDevice: Boolean = false,
    alreadyOnServer: Boolean = false,
    serverTargets: List<SourcePlugin> = emptyList(),
    onUploadToServer: ((String) -> Unit)? = null,
    transferProgress: Float? = null,
    transferLabel: String? = null,
    onCancelTransfer: (() -> Unit)? = null,
    sleepTimerRemainingMs: Long? = null,
    sleepAtEndOfTrack: Boolean = false,
    onSetSleepTimer: (Int?) -> Unit = {},
    onToggleSleepAtEnd: () -> Unit = {},
    queue: List<Track> = emptyList(),
    queueIndex: Int = -1,
    onPlayFromQueue: (Track) -> Unit = {},
    onMoveQueueTrack: ((Int, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var queueOpen by remember { mutableStateOf(false) }

    if (track == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing playing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val hasBackdrop = track.thumbnailUrl.isNotBlank()
    Box(modifier.fillMaxSize()) {
        if (hasBackdrop) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(track.thumbnailUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(48.dp)
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        val base = MaterialTheme.colorScheme

        val overlay = if (!hasBackdrop) base else base.copy(
            surface = Color(0xFF1D1D1F),
            surfaceContainer = Color(0xFF242428),
            surfaceVariant = Color(0x33FFFFFF),
            onSurface = Color.White,
            onSurfaceVariant = Color(0xB3FFFFFF),
            onBackground = Color.White
        )
        MaterialTheme(colorScheme = overlay) {
        Surface(
            color = Color.Transparent,
            contentColor = if (hasBackdrop) Color.White
            else MaterialTheme.colorScheme.onBackground
        ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .padding(if (landscape) 12.dp else 24.dp)
        ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = if (queueOpen) "Up next" else "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (queue.isNotEmpty()) {
                IconButton(onClick = { queueOpen = !queueOpen }) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = if (queueOpen) "Hide queue" else "Show queue",
                        tint = if (queueOpen) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (queueOpen) {
            val offset = queueIndex.coerceAtLeast(0)
            val upNext = queue.drop(offset)
            val reorderState = rememberReorderState()
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                itemsIndexed(upNext, key = { i, t -> "${i + offset}-${t.id}" }) { i, t ->
                    val index = i + offset
                    ListItem(
                        modifier = Modifier
                            .reorderableItem(reorderState, i)
                            .clickable { onPlayFromQueue(t) },
                        leadingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (onMoveQueueTrack != null) {
                                    DragHandle(
                                        Modifier.reorderHandle(
                                            state = reorderState,
                                            index = i,
                                            itemCount = upNext.size,
                                            key = t.id,
                                            onMove = { from, to ->
                                                onMoveQueueTrack(from + offset, to + offset)
                                            }
                                        )
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                TrackThumbnail(t.thumbnailUrl, size = 40.dp)
                            }
                        },
                        headlineContent = {
                            Text(
                                t.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (index == queueIndex) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        supportingContent = {
                            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            if (index == queueIndex) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    )
                }
            }
        } else {
        val artwork: @Composable (Modifier) -> Unit = { mod ->
            Box(
                modifier = mod
                    .aspectRatio(1f)
                    .swipeToSkip(
                        onNext = { if (hasNext) onNext() },
                        onPrevious = { if (hasPreviousTrack) onPreviousTrack() }
                    )
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(24.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val artIcon = @Composable {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(96.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (track.thumbnailUrl.isBlank()) {
                    artIcon()
                } else {
                    val artUrl = if (LocalHighQualityArt.current) track.thumbnailUrl
                    else track.thumbnailUrl
                    SubcomposeAsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(artUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                        error = { artIcon() },
                        loading = { artIcon() },
                        success = { SubcomposeAsyncImageContent() }
                    )
                }
        }
        }
        val titles: @Composable () -> Unit = {
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = playback.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        val controls: @Composable () -> Unit = {
            SeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.padding(top = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onPrevious, enabled = hasPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
                }

                val playing = playback == PlaybackState.PLAYING || playback == PlaybackState.BUFFERING
                FilledIconButton(
                    onClick = { if (playing) onPause() else onPlay() },
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = onNext, enabled = hasNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next")
                }

                Box {
                    var moreOpen by remember { mutableStateOf(false) }
                    val busy = transferProgress != null

                    val canSaveToDevice = canDownload && !alreadyOnDevice
                    val canUpload = onUploadToServer != null && serverTargets.isNotEmpty() && !alreadyOnServer
                    IconButton(onClick = { moreOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Add to playlist") },
                            leadingIcon = { Icon(Icons.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = { moreOpen = false; onAddToPlaylist() }
                        )
                        if (canSaveToDevice) {
                            DropdownMenuItem(
                                text = { Text("Save to this device") },
                                leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                enabled = !busy,
                                onClick = { moreOpen = false; onDownload() }
                            )
                        }
                        if (canUpload) {
                            serverTargets.forEach { target ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (serverTargets.size == 1) "Upload to server"
                                            else "Upload to ${target.name}"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CloudUpload, contentDescription = null) },
                                    enabled = !busy,
                                    onClick = { moreOpen = false; onUploadToServer!!(target.baseUrl) }
                                )
                            }
                        }

                        listOf(15, 30, 60).forEach { min ->
                            DropdownMenuItem(
                                text = { Text("Sleep in $min min") },
                                leadingIcon = { Icon(Icons.Filled.Bedtime, contentDescription = null) },
                                onClick = { moreOpen = false; onSetSleepTimer(min) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (sleepAtEndOfTrack) "Don't sleep at end of track" else "Sleep at end of track") },
                            leadingIcon = {
                                Icon(
                                    Icons.Filled.Bedtime,
                                    contentDescription = null,
                                    tint = if (sleepAtEndOfTrack) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            onClick = { moreOpen = false; onToggleSleepAtEnd() }
                        )
                        if (sleepTimerRemainingMs != null) {
                            DropdownMenuItem(
                                text = { Text("Cancel sleep timer") },
                                leadingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                                onClick = { moreOpen = false; onSetSleepTimer(null) }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = if (shuffle) "Shuffle on" else "Shuffle off",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = when (repeatMode) {
                            RepeatMode.OFF -> "Repeat off"
                            RepeatMode.ALL -> "Repeat all"
                            RepeatMode.ONE -> "Repeat one"
                        },
                        tint = if (repeatMode == RepeatMode.OFF)
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (sleepTimerRemainingMs != null || sleepAtEndOfTrack) {
                val parts = buildList {
                    sleepTimerRemainingMs?.let { add("sleeping in ${formatTime(it)}") }
                    if (sleepAtEndOfTrack) add("sleeping at end of track")
                }
                Text(
                    text = "🌙 " + parts.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (transferProgress != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (transferProgress < 0f) {
                            LinearProgressIndicator(modifier = Modifier.weight(1f))
                        } else {
                            LinearProgressIndicator(
                                progress = { transferProgress },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (transferProgress < 1f && onCancelTransfer != null) {
                            IconButton(onClick = onCancelTransfer) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel")
                            }
                        }
                    }
                    if (!transferLabel.isNullOrBlank()) {
                        Text(
                            text = transferLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                artwork(Modifier.fillMaxHeight())
                Spacer(Modifier.width(20.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    titles()
                    controls()
                }
            }
        } else {
            artwork(Modifier.fillMaxWidth().padding(vertical = 24.dp))
            titles()
            controls()
        }
        }
        }
        }
        }
        }
    }
}

private fun Modifier.swipeToSkip(
    onNext: () -> Unit,
    onPrevious: () -> Unit
): Modifier = pointerInput(onNext, onPrevious) {
    val threshold = SWIPE_SKIP_THRESHOLD.toPx()
    var travelled = 0f
    detectHorizontalDragGestures(
        onDragStart = { travelled = 0f },
        onDragEnd = {
            when {
                travelled <= -threshold -> onNext()
                travelled >= threshold -> onPrevious()
            }
        },
        onDragCancel = { travelled = 0f },
        onHorizontalDrag = { change, delta ->
            travelled += delta

            change.consume()
        }
    )
}

private val SWIPE_SKIP_THRESHOLD = 64.dp

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }
    val max = durationMs.coerceAtLeast(1L).toFloat()
    val shown = if (dragging) dragValue else positionMs.toFloat().coerceIn(0f, max)

    Column(modifier = modifier.fillMaxWidth()) {
        ThinSlider(
            value = (shown / max).coerceIn(0f, 1f),
            enabled = durationMs > 0L,
            onValueChange = { fraction -> dragging = true; dragValue = fraction * max },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                dragging = false
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTime(shown.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (durationMs > 0L) formatTime(durationMs) else "--:--",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
