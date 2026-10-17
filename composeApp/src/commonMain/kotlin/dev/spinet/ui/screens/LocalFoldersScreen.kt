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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp

data class LocalFolderEntry(val name: String, val count: Int)

@Composable
fun LocalFoldersScreen(
    title: String,
    allCount: Int,
    folders: List<LocalFolderEntry>,
    loading: Boolean,
    onBack: () -> Unit,
    onOpenAll: () -> Unit,
    onPlayAll: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onPlayFolder: (String) -> Unit,
    onDeleteFolder: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf<LocalFolderEntry?>(null) }

    confirmDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete folder?") },
            text = { Text("Permanently delete \"${folder.name}\" and its ${folder.count} tracks from this device?") },
            confirmButton = {
                TextButton(onClick = { onDeleteFolder(folder.name); confirmDelete = null }) { Text("Delete") }
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            allCount == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No music on this device yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "__all__") {
                    FolderCard(
                        name = "All tracks",
                        count = allCount,
                        isAll = true,
                        onOpen = onOpenAll,
                        onPlay = onPlayAll
                    )
                }
                items(folders, key = { it.name }) { folder ->
                    FolderCard(
                        name = folder.name,
                        count = folder.count,
                        isAll = false,
                        onOpen = { onOpenFolder(folder.name) },
                        onPlay = { onPlayFolder(folder.name) },
                        onDelete = { confirmDelete = folder }
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderCard(
    name: String,
    count: Int,
    isAll: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpen() }
    ) {
        ListItem(
            colors = cardListItemColors(),
            leadingContent = {
                Icon(
                    if (isAll) Icons.Filled.LibraryMusic else Icons.Filled.Folder,
                    contentDescription = null
                )
            },
            headlineContent = { Text(name) },
            supportingContent = { Text("$count tracks") },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (count > 0) {
                        IconButton(onClick = onPlay) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play folder",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (onDelete != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Delete folder") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = { menuOpen = false; onDelete() }
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}
