package dev.spinet.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
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
import androidx.compose.ui.unit.dp
import dev.spinet.model.Playlist
import dev.spinet.model.SourcePlugin
import dev.spinet.ui.rememberJsonOpener
import dev.spinet.ui.rememberJsonSaver

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    favoritesCount: Int,
    mostPlayedCount: Int = 0,
    browsableSources: List<SourcePlugin>,
    localFolderLabel: String,
    localTrackCount: Int,
    onOpenFavorites: () -> Unit,
    onOpenMostPlayed: () -> Unit = {},
    onOpenPlaylist: (String) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit = {},
    onOpenLocal: () -> Unit,
    onBrowseSource: (SourcePlugin) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onImportPlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    onDeletePlaylist: (String) -> Unit = {},
    onMovePlaylist: (Int, Int) -> Unit = { _, _ -> },

    onDownloadPlaylist: (Playlist) -> Unit = {},

    onUploadPlaylist: ((Playlist) -> Unit)? = null,

    exportPlaylistJson: ((Playlist) -> String)? = null,

    syncAvailable: Boolean = false,
    onSyncPlaylists: () -> Unit = {},
    onFetchPlaylists: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val saveJson = rememberJsonSaver()
    val importPlaylist = rememberJsonOpener(onImportPlaylist)
    var newName by remember { mutableStateOf("") }

    var creating by remember { mutableStateOf(false) }
    val reorderState = rememberReorderState()

    var renameTarget by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onCreatePlaylist(newName.trim())
                        newName = ""
                        creating = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    onRenamePlaylist(target.id, renameText.trim())
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),

        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionTitle("Browse sources", Modifier.padding(bottom = 4.dp)) }
        item {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onOpenLocal() },
                leadingContent = {
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text("On this phone") },
                supportingContent = {
                    Text(
                        if (localFolderLabel.isBlank()) "Pick a music folder on your device"
                        else "$localFolderLabel · $localTrackCount tracks"
                    )
                }
            )
        }
        items(browsableSources, key = { it.id }) { source ->
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onBrowseSource(source) },
                leadingContent = {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                headlineContent = { Text(source.name) },
                supportingContent = { Text("Browse folders on this backend") }
            )
        }
        item { SectionTitle("Your library", Modifier.padding(top = 16.dp, bottom = 4.dp)) }
        item {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onOpenFavorites() },
                leadingContent = {
                    Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                headlineContent = { Text("Liked Songs") },
                supportingContent = { Text("$favoritesCount tracks") }
            )
        }

        if (mostPlayedCount > 0) {
            item {
                ListItem(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenMostPlayed() },
                    leadingContent = {
                        Icon(
                            Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    headlineContent = { Text("Most Played") },
                    supportingContent = { Text("Your top $mostPlayedCount songs") }
                )
            }
        }

        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { SectionTitle("Playlists") }

                    OutlinedButton(
                        onClick = importPlaylist,
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedIconButton(onClick = { newName = ""; creating = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New playlist")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedIconButton(onClick = onSyncPlaylists, enabled = syncAvailable) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Sync to server")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedIconButton(onClick = onFetchPlaylists, enabled = syncAvailable) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Fetch from server")
                    }
                }
                if (!syncAvailable) {
                    Text(
                        text = "Set up your server in Settings to sync playlists across devices.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
            var menuOpen by remember { mutableStateOf(false) }
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
                    .reorderableItem(reorderState, index)
                    .combinedClickable(
                        onClick = { onOpenPlaylist(playlist.id) },
                        onLongClick = { menuOpen = true }
                    )
            ) {
                ListItem(
                    colors = cardListItemColors(),
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DragHandle(
                                Modifier.reorderHandle(
                                    state = reorderState,
                                    index = index,
                                    itemCount = playlists.size,
                                    key = playlist.id,
                                    onMove = onMovePlaylist
                                )
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                        }
                    },
                    headlineContent = { Text(playlist.name) },
                    supportingContent = { Text("${playlist.tracks.size} tracks") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (playlist.tracks.isNotEmpty()) {
                                IconButton(onClick = { onPlayPlaylist(playlist) }) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play playlist",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Playlist options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuOpen = false
                                        renameText = playlist.name
                                        renameTarget = playlist
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.Download, null) },
                                    text = { Text("Download to device") },
                                    onClick = { menuOpen = false; onDownloadPlaylist(playlist) }
                                )
                                if (onUploadPlaylist != null) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.CloudUpload, null) },
                                        text = { Text("Upload to server") },
                                        onClick = { menuOpen = false; onUploadPlaylist(playlist) }
                                    )
                                }
                                if (exportPlaylistJson != null) {
                                    DropdownMenuItem(
                                        leadingIcon = { Icon(Icons.Filled.FileUpload, null) },
                                        text = { Text("Export as JSON") },
                                        onClick = {
                                            menuOpen = false
                                            saveJson("${playlist.name}.json", exportPlaylistJson(playlist))
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Filled.DeleteOutline, null) },
                                    text = { Text("Delete playlist") },
                                    onClick = { menuOpen = false; onDeletePlaylist(playlist.id) }
                                )
                            }
                            }
                        }
                    }
                )
            }
        }
    }
}
