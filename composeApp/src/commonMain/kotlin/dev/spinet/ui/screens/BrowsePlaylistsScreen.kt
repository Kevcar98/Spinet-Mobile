package dev.spinet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.spinet.data.BrowsePlaylistDto

@Composable
fun BrowsePlaylistsScreen(
    sourceName: String,
    playlists: List<BrowsePlaylistDto>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onOpenPlaylist: (BrowsePlaylistDto) -> Unit,

    onSearch: ((String) -> Unit)? = null,
    onPlayPlaylist: (BrowsePlaylistDto) -> Unit = {},
    onDeletePlaylist: (BrowsePlaylistDto) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var openMenuId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<BrowsePlaylistDto?>(null) }

    confirmDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete folder?") },
            text = { Text("Permanently delete \"${pl.name}\" and its ${pl.trackCount} tracks from the server?") },
            confirmButton = {
                TextButton(onClick = { onDeletePlaylist(pl); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = sourceName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (onSearch != null) {
            var query by remember { mutableStateOf("") }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                placeholder = { Text("Search this server") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenPlaylist(pl) }
                    ) {
                        ListItem(
                            colors = cardListItemColors(),
                            leadingContent = {
                                Icon(
                                    if (pl.id == "__all__") Icons.Filled.LibraryMusic else Icons.Filled.Folder,
                                    contentDescription = null
                                )
                            },
                            headlineContent = { Text(pl.name) },
                            supportingContent = { Text("${pl.trackCount} tracks") },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (pl.trackCount > 0) {
                                        IconButton(onClick = { onPlayPlaylist(pl) }) {
                                            Icon(
                                                Icons.Filled.PlayArrow,
                                                contentDescription = "Play folder",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    if (pl.id != "__all__") {
                                        Box {
                                            IconButton(onClick = { openMenuId = pl.id }) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                            }
                                            DropdownMenu(
                                                expanded = openMenuId == pl.id,
                                                onDismissRequest = { openMenuId = null }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Delete folder") },
                                                    leadingIcon = {
                                                        Icon(Icons.Filled.Delete, contentDescription = null)
                                                    },
                                                    onClick = { openMenuId = null; confirmDelete = pl }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
