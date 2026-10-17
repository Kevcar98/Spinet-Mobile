package dev.spinet.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.spinet.extensions.EntryStatus
import dev.spinet.extensions.ExtensionRepositories
import dev.spinet.extensions.OnAppResumed
import dev.spinet.extensions.PickExtensionFileButton
import dev.spinet.extensions.RepoEntryUi
import dev.spinet.extensions.RepoUi
import dev.spinet.util.openUrlExternally

@Composable
fun ExtensionRepositoriesCard(repositories: ExtensionRepositories) {
    val state by repositories.state.collectAsState()
    var url by remember { mutableStateOf("") }

    LaunchedEffect(repositories) { repositories.loadIfNeeded() }

    OnAppResumed { repositories.onResumed() }

    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Extensions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (state.repos.isNotEmpty()) {
                    IconButton(onClick = repositories::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check repositories again")
                    }
                }
            }
            Text(
                text = "Spinet comes with no sources of its own. Install them from files you downloaded " +
                    "(pick several at once if you like), or from a repository you add. Installed sources " +
                    "appear above, switched off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            PickExtensionFileButton("Install from file…") { files ->
                repositories.installFromFiles(files)
            }
            TextButton(onClick = { openUrlExternally(EXTENSION_GUIDE_URL) }) {
                Text("How to make your own extension")
            }

            HorizontalDivider()
            Text("Repositories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = "None are included. Add the address of one you trust; nothing is installed or " +
                    "updated unless you choose it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text("Repository URL") },
                    placeholder = { Text("https://example.org/spinet") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { repositories.add(url); url = "" }, enabled = url.isNotBlank()) {
                    Text("Add")
                }
            }

            state.message?.let { message ->
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            state.repos.forEach { repo ->
                HorizontalDivider()
                RepoSection(repo, repositories)
            }
        }
    }
}

@Composable
private fun RepoSection(repo: RepoUi, repositories: ExtensionRepositories) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    repo.name.ifBlank { "Repository" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (repo.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            IconButton(onClick = { repositories.remove(repo.url) }) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove repository")
            }
        }
        repo.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!repo.loading && repo.error == null && repo.entries.isEmpty() && repo.name.isNotBlank()) {
            Text("This repository offers nothing.", style = MaterialTheme.typography.bodySmall)
        }
        repo.entries.forEach { entry ->
            EntryRow(entry, onInstall = { repositories.install(repo.url, entry.extension) })
        }
    }
}

@Composable
private fun EntryRow(entry: RepoEntryUi, onInstall: () -> Unit) {
    val ext = entry.extension
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                "${ext.name}  ${ext.versionName.ifBlank { "v${ext.versionCode}" }}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val details = listOf(ext.description, ext.author.takeIf { it.isNotBlank() }?.let { "by $it" }, ext.license)
                .filterNot { it.isNullOrBlank() }
                .joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (ext.capabilities.isNotEmpty()) {
                Text(
                    "Can: " + ext.capabilities.joinToString(", ") { it.lowercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        when {
            entry.busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            entry.status == EntryStatus.AVAILABLE -> Button(onClick = onInstall) { Text("Install") }
            entry.status == EntryStatus.UPDATE -> OutlinedButton(onClick = onInstall) { Text("Update") }
            else -> Text(
                when (entry.status) {
                    EntryStatus.INSTALLED -> "Installed"
                    EntryStatus.RESTART -> "Restart to load"
                    EntryStatus.NEEDS_NEWER_APP -> "Needs a newer Spinet"
                    EntryStatus.NOT_FOR_PLATFORM -> "Not for this device"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val EXTENSION_GUIDE_URL =
    "https://github.com/Kevcar98/Spinet-Modular/blob/main/docs/WRITING-EXTENSIONS.md"
