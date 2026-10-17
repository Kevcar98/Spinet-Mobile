package dev.spinet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

@Composable
fun SearchScreen(
    query: String,
    results: List<Track>,
    isSearching: Boolean,
    error: String?,
    downloadStatus: String?,
    favoriteTrackIds: Set<String>,
    serverTargets: List<SourcePlugin>,
    onQueryChange: (String) -> Unit,
    onTrackClick: (Track) -> Unit,
    onDownloadToPhone: (Track) -> Unit,
    onDownloadToServer: (Track, String) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDeleteFromServer: ((Track) -> Unit)? = null,
    isOnDevice: (Track) -> Boolean = { false },
    isOnServer: (Track) -> Boolean = { it.serverBaseUrl.isNotBlank() },
    currentTrackKey: String? = null,
    playingProgress: Float = 0f,
    searchHistory: List<String> = emptyList(),
    onClearHistory: () -> Unit = {},
    onRemoveHistory: (String) -> Unit = {},

    identifyContent: @Composable () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = { identifyContent() },
            placeholder = { Text("Search, or paste a link") },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        )

        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        if (downloadStatus != null) {
            Text(
                text = downloadStatus,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (query.isBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) { SectionTitle("Recent searches") }

                if (searchHistory.isNotEmpty()) {
                    TextButton(onClick = onClearHistory) { Text("Clear all") }
                }
            }
            if (searchHistory.isEmpty()) {
                Text(
                    text = "No searches yet. What you search for shows up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(searchHistory, key = { it }) { past ->
                        ListItem(
                            headlineContent = { Text(past, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                            trailingContent = {
                                IconButton(onClick = { onRemoveHistory(past) }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove \"$past\" from recent searches",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.clickable { onQueryChange(past) }
                        )
                    }
                }
            }
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results, key = { it.id }) { track ->
                val isCurrent = currentTrackKey != null && track.matchKey() == currentTrackKey
                TrackRow(
                    track = track,
                    onClick = { onTrackClick(track) },
                    subtitle = listOf(track.artist, track.durationLabel)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    isFavorite = track.id in favoriteTrackIds,
                    alsoOnDevice = isOnDevice(track),
                    alsoOnServer = isOnServer(track),
                    isCurrent = isCurrent,
                    progress = if (isCurrent) playingProgress else 0f,
                    serverTargets = serverTargets,
                    onToggleFavorite = { onToggleFavorite(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onSaveToDevice = { onDownloadToPhone(track) },
                    onSaveToServer = { baseUrl -> onDownloadToServer(track, baseUrl) },
                    onDeleteFromServer = onDeleteFromServer?.let { cb -> { cb(track) } }
                )
            }
        }
    }
}
