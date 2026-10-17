package dev.spinet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.spinet.recognition.CaptureSource
import dev.spinet.recognition.IdentifyPhase
import dev.spinet.recognition.RecognizedSong

@Composable
fun IdentifySongAction(
    phase: IdentifyPhase,
    result: RecognizedSong?,
    error: String?,
    sources: List<CaptureSource>,

    onStart: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSearchResult: () -> Unit,
) {
    if (sources.isEmpty()) return

    var chooserOpen by remember { mutableStateOf(false) }
    var selectedSource by remember { mutableStateOf(sources.first().id) }

    IconButton(onClick = {
        if (sources.size > 1) chooserOpen = true else onStart(sources.first().id, false)
    }) {
        Icon(Icons.Filled.Mic, contentDescription = "Identify song")
    }

    if (chooserOpen) {
        AlertDialog(
            onDismissRequest = { chooserOpen = false },
            title = { Text("Listen with…") },
            text = {
                Column {
                    Text(
                        "Pick the microphone for music playing around you, or a system-audio device to identify what this computer is playing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sources.forEach { source ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        ) {
                            RadioButton(
                                selected = selectedSource == source.id,
                                onClick = { selectedSource = source.id },
                            )
                            Icon(
                                if (source.isSystemAudio) Icons.Filled.Speaker else Icons.Filled.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                source.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    chooserOpen = false
                    onStart(selectedSource, false)
                }) { Text("Listen") }
            },
            dismissButton = {
                TextButton(onClick = { chooserOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (phase != IdentifyPhase.IDLE) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    when (phase) {
                        IdentifyPhase.LISTENING -> "Listening…"
                        IdentifyPhase.MATCHING -> "Matching…"
                        IdentifyPhase.RESULT -> "Found it"
                        IdentifyPhase.NO_MATCH -> "No match"
                        else -> "Identify failed"
                    }
                )
            },
            text = {
                when (phase) {
                    IdentifyPhase.LISTENING, IdentifyPhase.MATCHING -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            if (phase == IdentifyPhase.LISTENING)
                                "Keep the music playing while a short clip is recorded."
                            else "Finding the track…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    IdentifyPhase.RESULT -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (result?.coverUrl != null) {
                            AsyncImage(
                                model = result.coverUrl,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(result?.title ?: "", style = MaterialTheme.typography.titleMedium)
                            Text(
                                result?.artist ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IdentifyPhase.NO_MATCH -> Text(
                        "Couldn't recognize that. If someone is talking over the music, " +
                            "\"Listen longer\" records for 20 seconds and tries several " +
                            "moments, otherwise turn it up a little and retry."
                    )
                    else -> Text(error ?: "Something went wrong. Try again.")
                }
            },
            confirmButton = {
                when (phase) {
                    IdentifyPhase.RESULT -> TextButton(onClick = onSearchResult) { Text("Search in app") }

                    IdentifyPhase.NO_MATCH -> TextButton(onClick = {
                        onStart(selectedSource, true)
                    }) { Text("Listen longer") }
                    IdentifyPhase.ERROR -> TextButton(onClick = {
                        onStart(selectedSource, false)
                    }) { Text("Try again") }
                    else -> {}
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(if (phase == IdentifyPhase.LISTENING || phase == IdentifyPhase.MATCHING) "Cancel" else "Close")
                }
            },
        )
    }
}
