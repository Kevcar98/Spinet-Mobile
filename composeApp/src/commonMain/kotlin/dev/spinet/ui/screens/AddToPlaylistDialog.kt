package dev.spinet.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import dev.spinet.model.Playlist
import dev.spinet.model.Track
import dev.spinet.model.matchKey

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    tracks: List<Track>,
    onDismiss: () -> Unit,
    onAdd: (playlistId: String, tracks: List<Track>) -> Unit,
    onRemove: (playlistId: String, tracks: List<Track>) -> Unit,
    onCreate: (name: String, tracks: List<Track>) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    val keys = tracks.map { it.matchKey() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = {
            Text(if (tracks.size == 1) "Add to playlist" else "Add ${tracks.size} songs to playlist")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                playlists.forEach { playlist ->

                    val present = keys.count { key ->
                        playlist.tracks.any { it.matchKey() == key }
                    }
                    val all = present == tracks.size && tracks.isNotEmpty()
                    val some = present in 1 until tracks.size

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (all) onRemove(playlist.id, tracks) else onAdd(playlist.id, tracks)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.PlaylistPlay, contentDescription = null)
                        Text(
                            text = playlist.name,
                            modifier = Modifier.padding(start = 12.dp).weight(1f)
                        )
                        when {
                            all -> Icon(
                                Icons.Filled.Check,
                                contentDescription = if (tracks.size == 1) {
                                    "Already in this playlist: tap to remove"
                                } else {
                                    "All ${tracks.size} already here: tap to remove them"
                                },
                                tint = MaterialTheme.colorScheme.primary
                            )

                            some -> Text(
                                text = "$present of ${tracks.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("New playlist") },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            onCreate(newName, tracks)
                            newName = ""
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Create with these songs")
                    }
                }
            }
        }
    )
}
