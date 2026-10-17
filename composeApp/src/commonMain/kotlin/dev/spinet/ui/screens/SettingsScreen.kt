package dev.spinet.ui.screens

import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.spinet.data.PlaylistImportResult
import dev.spinet.model.SourceKind
import dev.spinet.local.LocalFolder
import dev.spinet.local.PickLocalFolderButton
import dev.spinet.model.SourcePlugin
import dev.spinet.viewmodel.ServerEntry
import dev.spinet.model.ThemeSpec
import dev.spinet.theme.toColorOrNull
import dev.spinet.ui.ExportPluginFileButton
import dev.spinet.ui.ImportPluginFileButton
import dev.spinet.ui.OpenCsvFileButton

@Composable
fun SettingsScreen(
    extensionSettingsFor: (String) -> dev.spinet.extensions.ExtensionSettingsUi? = { null },
    onSaveExtensionSettings: (String, Map<String, String>) -> Unit = { _, _ -> },

    extensionRepositories: dev.spinet.extensions.ExtensionRepositories? = null,
    experimentalEnabled: Boolean,
    sources: List<SourcePlugin>,
    backendTestStatus: String?,
    themes: List<ThemeSpec>,
    selectedThemeId: String,
    importStatus: String?,
    playlistImportStatus: String?,
    playlistImportPreview: PlaylistImportResult?,
    onImportPlaylistLink: (String) -> Unit,
    onImportCsvPlaylist: (String) -> Unit,
    onConfirmPlaylistImport: (String) -> Unit,
    onCancelPlaylistImport: () -> Unit,
    onExperimentalToggle: (Boolean) -> Unit,
    highQualityArt: Boolean,
    onHighQualityArtToggle: (Boolean) -> Unit,
    serverHost: String,
    servers: List<ServerEntry> = emptyList(),
    serverConfigStatus: String?,
    onSaveServer: (String?, String, String, String, String) -> Unit,
    onRemoveServer: (String) -> Unit = {},
    onMoveServer: (String, Boolean) -> Unit = { _, _ -> },
    defaultServerDir: String = "",
    serverFolders: List<String> = emptyList(),
    serverFoldersLoading: Boolean = false,
    onLoadServerFolders: () -> Unit = {},
    onPickServerFolder: (String) -> Unit = {},
    localFolderLabel: String,
    localTrackCount: Int,
    onPickLocalFolder: (LocalFolder) -> Unit,
    onRefreshLocal: () -> Unit,
    onRetagDownloads: () -> Unit = {},
    onToggleSource: (String, Boolean) -> Unit,
    onInstallSource: (String, String) -> Unit,
    onUninstallSource: (String) -> Unit,
    onRenameSource: (String, String) -> Unit,
    onUpdateSource: (String, String, String) -> Unit,
    onTestBackend: (String) -> Unit,
    onSelectTheme: (String) -> Unit,
    onInstallTheme: (String, String, String, String, String, Boolean) -> Unit,
    onUninstallTheme: (String) -> Unit,
    onImportPlugin: (String) -> Unit,
    onExportPlugins: () -> String,
    sectionOrder: List<String>,
    locked: Boolean,
    onReorder: (List<String>) -> Unit,
    onToggleLock: (Boolean) -> Unit,

    appVersion: String = "",
    updateStatus: String? = null,
    updateBusy: Boolean = false,
    onCheckForUpdates: (() -> Unit)? = null,
    autoSync: Boolean = false,
    onToggleAutoSync: () -> Unit = {},
    themeSync: Boolean = true,
    onToggleThemeSync: () -> Unit = {},
    playbackSync: Boolean = true,
    onTogglePlaybackSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val syncAvailable = serverHost.isNotBlank()

    val defaultOrder = listOf(
        "updates", "appearance", "server", "local", "sources", "import_export",
        "csv"
    )

    val order = (listOf("updates") + sectionOrder.filter { it in defaultOrder } + defaultOrder)
        .distinct()

    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        val m = order.toMutableList()
        m.add(to, m.removeAt(from))
        onReorder(m)
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { LayoutLockCard(locked = locked, onToggleLock = onToggleLock) }

        itemsIndexed(order, key = { _, key -> key }) { index, key ->
            SettingsSection(
                title = sectionTitleFor(key),
                locked = locked,
                canMoveUp = index > 0,
                canMoveDown = index < order.lastIndex,
                onMoveUp = { move(index, index - 1) },
                onMoveDown = { move(index, index + 1) }
            ) {
                when (key) {
                    "appearance" -> {
                        HighQualityArtCard(enabled = highQualityArt, onToggle = onHighQualityArtToggle)
                        ThemesCard(
                            themes = themes,
                            selectedThemeId = selectedThemeId,
                            onSelect = onSelectTheme,
                            onUninstall = onUninstallTheme
                        )
                        InstallThemeCard(onInstall = onInstallTheme)
                        SyncToggleCard(
                            title = "Sync theme & look",
                            subtitle = "Your custom themes and the active theme follow the server sync.",
                            checked = themeSync,
                            enabled = syncAvailable,
                            onToggle = onToggleThemeSync
                        )
                    }
                    "server" -> {
                        MyServersCard(
                            servers = servers,
                            status = serverConfigStatus,
                            onSave = onSaveServer,
                            onRemove = onRemoveServer,
                            onMove = onMoveServer
                        )
                        if (serverHost.isNotBlank()) {
                            UploadFolderCard(
                                defaultDir = defaultServerDir,
                                serverFolders = serverFolders,
                                foldersLoading = serverFoldersLoading,
                                onLoadFolders = onLoadServerFolders,
                                onPickFolder = onPickServerFolder
                            )
                        }
                        SyncToggleCard(
                            title = "Auto-sync library",
                            subtitle = "Push playlist/like changes to the server and fetch them at startup.",
                            checked = autoSync,
                            enabled = syncAvailable,
                            onToggle = onToggleAutoSync
                        )
                        SyncToggleCard(
                            title = "Sync last played",
                            subtitle = "Carry the current song and position between your devices, " +
                                "so each one picks up where the last left off.",
                            checked = playbackSync,
                            enabled = syncAvailable,
                            onToggle = onTogglePlaybackSync
                        )
                    }
                    "local" -> LocalFolderCard(
                        folderLabel = localFolderLabel,
                        trackCount = localTrackCount,
                        onPick = onPickLocalFolder,
                        onRefresh = onRefreshLocal,
                        onRetagDownloads = onRetagDownloads
                    )
                    "sources" -> {
                        ExperimentalToggleCard(enabled = experimentalEnabled, onToggle = onExperimentalToggle)
                        if (experimentalEnabled) {
                            InstalledSourcesCard(
                                sources = sources,
                                settingsFor = extensionSettingsFor,
                                onSaveSettings = onSaveExtensionSettings,
                                onToggle = onToggleSource,
                                onUninstall = onUninstallSource,
                                onRename = onRenameSource,
                                onUpdate = onUpdateSource
                            )
                            InstallPluginCard(
                                testStatus = backendTestStatus,
                                onTest = onTestBackend,
                                onInstall = onInstallSource
                            )
                            if (extensionRepositories != null) {
                                ExtensionRepositoriesCard(extensionRepositories)
                            }
                        }
                    }
                    "import_export" -> ImportPluginCard(
                        importStatus = importStatus,
                        onImport = onImportPlugin,
                        onExport = onExportPlugins
                    )
                    "csv" -> CsvImportCard(
                        status = playlistImportStatus,
                        preview = playlistImportPreview,
                        onImportLink = onImportPlaylistLink,
                        onImportCsv = onImportCsvPlaylist,
                        onConfirm = onConfirmPlaylistImport,
                        onCancel = onCancelPlaylistImport
                    )
                    "updates" -> {
                        if (onCheckForUpdates != null) {
                            UpdatesCard(
                                version = appVersion,
                                status = updateStatus,
                                busy = updateBusy,
                                onCheck = onCheckForUpdates
                            )
                        }
                        LogCard()
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun UpdatesCard(
    version: String,
    status: String?,
    busy: Boolean,
    onCheck: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Version $version", style = MaterialTheme.typography.titleSmall)
            Text(
                "Updates are downloaded from this app's GitHub releases. You are always asked before anything is installed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Button(
                onClick = onCheck,
                enabled = !busy,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text(if (busy) "Checking\u2026" else "Check for updates")
            }
        }
    }
}

private fun sectionTitleFor(key: String): String = when (key) {
    "appearance" -> "Appearance"
    "server" -> "My Servers"
    "local" -> "Local music"
    "sources" -> "Sources"
    "import_export" -> "Import / export"
    "csv" -> "Import playlist from CSV"
    "updates" -> "Updates and help"
    else -> key
}

@Composable
private fun SettingsSection(
    title: String,
    locked: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SectionTitle(title) }
            if (!locked) {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move section up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move section down")
                }
            }
        }
        content()
    }
}

@Composable
private fun LayoutLockCard(locked: Boolean, onToggleLock: (Boolean) -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Lock layout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (locked) "Unlock to drag sections into your preferred order."
                        else "Use the arrows on each section to reorder. Lock when done.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = locked, onCheckedChange = onToggleLock)
        }
    }
}

@Composable
private fun HighQualityArtCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.HighQuality,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("High-quality artwork", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Fetch large, crisp cover art. Turn off to save data with smaller images.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ThemesCard(
    themes: List<ThemeSpec>,
    selectedThemeId: String,
    onSelect: (String) -> Unit,
    onUninstall: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            themes.forEach { theme ->
                ThemeRow(
                    theme = theme,
                    selected = theme.id == selectedThemeId,
                    onSelect = { onSelect(theme.id) },
                    onUninstall = { onUninstall(theme.id) }
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    theme: ThemeSpec,
    selected: Boolean,
    onSelect: () -> Unit,
    onUninstall: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Swatch(theme.primary)
            Swatch(theme.secondary)
            Swatch(theme.tertiary)
            Swatch(theme.background)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = theme.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
        if (!theme.builtIn) {
            IconButton(onClick = onUninstall) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Uninstall")
            }
        }
    }
}

@Composable
private fun Swatch(hex: String) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(hex.toColorOrNull() ?: MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
    )
}

@Composable
private fun InstallThemeCard(
    onInstall: (String, String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var primary by remember { mutableStateOf("") }
    var secondary by remember { mutableStateOf("") }
    var tertiary by remember { mutableStateOf("") }
    var background by remember { mutableStateOf("") }
    var dark by remember { mutableStateOf(false) }

    var expanded by remember { mutableStateOf(false) }

    val colorsValid = listOf(primary, secondary, tertiary, background).all { it.toColorOrNull() != null }
    val canInstall = name.isNotBlank() && colorsValid

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Install a theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }
            if (!expanded) return@Column
            Text(
                text = "Create a custom theme from hex colors (e.g. #6750A4). It's just data: nothing is downloaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(primary, { primary = it }, singleLine = true, label = { Text("Primary  #RRGGBB") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secondary, { secondary = it }, singleLine = true, label = { Text("Secondary  #RRGGBB") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(tertiary, { tertiary = it }, singleLine = true, label = { Text("Tertiary  #RRGGBB") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(background, { background = it }, singleLine = true, label = { Text("Background  #RRGGBB") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark theme", modifier = Modifier.weight(1f))
                Switch(checked = dark, onCheckedChange = { dark = it })
            }
            Button(
                onClick = {
                    onInstall(name.trim(), primary, secondary, tertiary, background, dark)
                    name = ""; primary = ""; secondary = ""; tertiary = ""; background = ""; dark = false
                },
                enabled = canInstall,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Install theme")
            }
        }
    }
}

@Composable
private fun ExperimentalToggleCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Science, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pluggable sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Pill(
                        icon = null,
                        text = "BETA",
                        container = MaterialTheme.colorScheme.tertiary,
                        content = MaterialTheme.colorScheme.onTertiary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Manage search sources: toggle them on/off, install your own backends, or uninstall ones you added. Saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun InstalledSourcesCard(
    sources: List<SourcePlugin>,
    settingsFor: (String) -> dev.spinet.extensions.ExtensionSettingsUi?,
    onSaveSettings: (String, Map<String, String>) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onUninstall: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onUpdate: (String, String, String) -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Installed sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Enabled sources are tried in order. Your servers come first, in the order set under My Servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            sources.forEach { plugin ->
                SourceRow(
                    plugin = plugin,
                    settings = if (plugin.kind == SourceKind.EXTENSION) settingsFor(plugin.id) else null,
                    onSaveSettings = { onSaveSettings(plugin.id, it) },
                    onToggle = { onToggle(plugin.id, it) },
                    onUninstall = { onUninstall(plugin.id) },
                    onRename = { onRename(plugin.id, it) },
                    onUpdate = { name, url -> onUpdate(plugin.id, name, url) }
                )
            }
        }
    }
}

@Composable
private fun SourceRow(
    plugin: SourcePlugin,
    settings: dev.spinet.extensions.ExtensionSettingsUi? = null,
    onSaveSettings: (Map<String, String>) -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit,
    onRename: (String) -> Unit,
    onUpdate: (String, String) -> Unit
) {
    val description = when (plugin.kind) {
        SourceKind.SERVER -> plugin.baseUrl
        SourceKind.EXTENSION -> plugin.baseUrl
    }

    val editableUrl = plugin.kind == SourceKind.SERVER
    var editing by remember(plugin.id) { mutableStateOf(false) }
    var confirmRemove by remember(plugin.id) { mutableStateOf(false) }
    var showSettings by remember(plugin.id) { mutableStateOf(false) }

    if (showSettings && settings != null) {
        ExtensionSettingsDialog(
            title = plugin.name,
            settings = settings,
            onDismiss = { showSettings = false },
            onSave = { onSaveSettings(it); showSettings = false }
        )
    }

    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${plugin.name}?") },
            text = {
                Text(
                    if (plugin.kind == SourceKind.EXTENSION) {
                        "This uninstalls the extension from this device. Songs from it stay in your " +
                            "library but won't play until it is installed again."
                    } else {
                        "This removes it from your sources. Nothing on the server itself is deleted."
                    }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = false; onUninstall() }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
    var editName by remember(plugin.id) { mutableStateOf(plugin.name) }
    var editUrl by remember(plugin.id) { mutableStateOf(plugin.baseUrl) }

    if (editing) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it },
                singleLine = true,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            if (editableUrl) {
                OutlinedTextField(
                    value = editUrl,
                    onValueChange = { editUrl = it },
                    singleLine = true,
                    label = { Text("URL") },
                    placeholder = { Text("http://192.168.1.98:8091") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = false }, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (editableUrl) onUpdate(editName, editUrl) else onRename(editName)
                        editing = false
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save")
                }
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plugin.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = plugin.enabled, onCheckedChange = onToggle)

            if (settings != null) {
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = "Settings")
                }
            }

            if (plugin.kind != SourceKind.EXTENSION) {
                IconButton(onClick = {
                    editName = plugin.name
                    editUrl = plugin.baseUrl
                    editing = true
                }) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
            }

            if (!plugin.builtIn || plugin.kind == SourceKind.EXTENSION) {
                IconButton(onClick = { confirmRemove = true }) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
private fun LocalFolderCard(
    folderLabel: String,
    trackCount: Int,
    onPick: (LocalFolder) -> Unit,
    onRefresh: () -> Unit,
    onRetagDownloads: () -> Unit = {}
) {
    val hasFolder = folderLabel.isNotBlank()
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Music on this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (hasFolder) "$folderLabel · $trackCount tracks"
                    else "Pick a folder on your device that holds your music. It plays in the app, even offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PickLocalFolderButton(
                    text = if (hasFolder) "Change folder" else "Choose music folder",
                    onPicked = onPick
                )
                if (hasFolder) {
                    OutlinedButton(onClick = onRefresh) { Text("Rescan") }
                }
            }
            if (hasFolder) {
                Text(
                    text = "Look up the official title, artist and cover art (via iTunes) and " +
                        "write them onto your downloaded M4A files.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onRetagDownloads, modifier = Modifier.fillMaxWidth()) {
                    Text("Fetch music info & cover art")
                }
            }
        }
    }
}

@Composable
private fun MyServersCard(
    servers: List<ServerEntry>,
    status: String?,
    onSave: (String?, String, String, String, String) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Boolean) -> Unit
) {
    var editing by remember { mutableStateOf<String?>(null) }
    val open = editing ?: if (servers.isEmpty()) NEW_SERVER else null

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your servers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (servers.size > 1) {
                    "Songs are looked for on these servers from the top down; if one isn't answering, " +
                        "the next one is used. Uploads, sync and your place in a song go to the top one."
                } else {
                    "Add the server you set up. You can add more, for example one for each person " +
                        "in the family, and choose the order they're used in."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            servers.forEachIndexed { index, server ->
                ServerRow(
                    server = server,
                    main = index == 0,
                    canMoveUp = index > 0,
                    canMoveDown = index < servers.lastIndex,
                    onMoveUp = { onMove(server.id, true) },
                    onMoveDown = { onMove(server.id, false) },
                    onEdit = { editing = if (editing == server.id) null else server.id },
                    onRemove = {
                        if (editing == server.id) editing = null
                        onRemove(server.id)
                    }
                )
                if (open == server.id) {
                    ServerForm(
                        server = server,
                        onSave = { name, address, user, pass ->
                            onSave(server.id, name, address, user, pass)
                            editing = null
                        },
                        onCancel = { editing = null }
                    )
                }
            }
            if (open == NEW_SERVER) {
                ServerForm(
                    server = null,
                    onSave = { name, address, user, pass ->
                        onSave(null, name, address, user, pass)
                        editing = null
                    },
                    onCancel = if (servers.isEmpty()) null else ({ editing = null })
                )
            } else {
                OutlinedButton(onClick = { editing = NEW_SERVER }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add a server")
                }
            }
            if (status != null) {
                StatusText(status, !status.contains("✗"))
            }
        }
    }
}

private const val NEW_SERVER = "new"

@Composable
private fun ServerRow(
    server: ServerEntry,
    main: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.name + if (main) " (main)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    server.baseUrl.substringAfter("://") +
                        (if (server.username.isNotBlank()) " · signed in as ${server.username}" else "") +
                        (if (!server.enabled) " · switched off" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = { confirmRemove = true }) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove")
            }
        }
    }
    if (confirmRemove) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove ${server.name}?") },
            text = { Text("The app stops using this server. Nothing on the server is deleted.") },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ServerForm(
    server: ServerEntry?,
    onSave: (name: String, address: String, username: String, password: String) -> Unit,
    onCancel: (() -> Unit)?
) {
    val address = server?.baseUrl.orEmpty()
    val hostAndPort = address.substringAfter("://")
    val givenPort = hostAndPort.substringAfter(':', "").substringBefore('/')

    var useHttps by remember(address) {
        mutableStateOf(address.isBlank() || address.startsWith("https://", ignoreCase = true))
    }
    var nameDraft by remember(server?.id) { mutableStateOf(server?.name.orEmpty()) }
    var hostDraft by remember(address) {
        mutableStateOf(if (givenPort.isEmpty()) hostAndPort else hostAndPort.removeSuffix(":$givenPort"))
    }
    var portDraft by remember(address) { mutableStateOf(givenPort.ifBlank { "8091" }) }
    var userDraft by remember(server?.id) { mutableStateOf(server?.username.orEmpty()) }
    var passDraft by remember(server?.id) { mutableStateOf(server?.password.orEmpty()) }
    var showPass by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (server == null) "New server" else "Change ${server.name}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            nameDraft, { nameDraft = it }, singleLine = true,
            label = { Text("Name (optional)") },
            placeholder = { Text("e.g. Mum's server") },
            modifier = Modifier.fillMaxWidth()
        )
        ServerKindOption(
            selected = useHttps,
            title = "HTTPS: a domain or DuckDNS name",
            subtitle = "The usual setup. No port needed.",
            onSelect = { useHttps = true }
        )
        ServerKindOption(
            selected = !useHttps,
            title = "Plain HTTP: an IP address and port",
            subtitle = "For a server set up without a domain.",
            onSelect = { useHttps = false }
        )
        OutlinedTextField(
            hostDraft,
            { typed ->

                val t = typed.trim()
                when {
                    t.startsWith("https://", ignoreCase = true) -> useHttps = true
                    t.startsWith("http://", ignoreCase = true) -> useHttps = false
                }
                hostDraft = t.substringAfter("://")
            },
            singleLine = true,
            label = { Text(if (useHttps) "Domain" else "IP address") },
            placeholder = { Text(if (useHttps) "yourname.duckdns.org" else "192.168.1.98") },
            prefix = { Text(if (useHttps) "https://" else "http://") },
            modifier = Modifier.fillMaxWidth()
        )
        if (!useHttps) {
            OutlinedTextField(
                portDraft, { portDraft = it }, singleLine = true,
                label = { Text("Library port") },
                placeholder = { Text("8091") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            "Sign-in: only if it's turned on for this server. Leave both empty otherwise.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            userDraft, { userDraft = it }, singleLine = true,
            label = { Text("Username") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            passDraft, { passDraft = it }, singleLine = true,
            label = { Text("Password") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Hide" else "Show") }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (onCancel != null) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            }
            Button(
                onClick = {
                    val host = hostDraft.trim().trimEnd('/')
                    val port = portDraft.trim()
                    val full = if (useHttps) "https://$host" else "http://$host" + (if (port.isBlank()) "" else ":$port")
                    onSave(nameDraft.trim(), full, userDraft.trim(), passDraft)
                },
                enabled = hostDraft.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save server")
            }
        }
    }
}

@Composable
private fun InstallPluginCard(
    testStatus: String?,
    onTest: (String) -> Unit,
    onInstall: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Install a source", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "Add a library server you run yourself (see our Docker image on GitHub). It's just a URL: no code is downloaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, placeholder = { Text("My home server") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Backend URL") }, placeholder = { Text("https://music.myhome.example") }, modifier = Modifier.fillMaxWidth())
            if (testStatus != null) {
                StatusText(testStatus, testStatus.startsWith("✓"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTest(url.trim()) }, enabled = url.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text("Test")
                }
                Button(
                    onClick = { onInstall(name.trim(), url.trim()); name = ""; url = "" },
                    enabled = name.isNotBlank() && url.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Install")
                }
            }
        }
    }
}

@Composable
private fun ImportPluginCard(importStatus: String?, onImport: (String) -> Unit, onExport: () -> String) {
    var pasted by remember { mutableStateOf("") }

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Share sources and themes as a .json plugin file. It's pure data: no code is loaded or executed. Unknown entries are ignored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportPluginFileButton(onFileRead = onImport)
                ExportPluginFileButton(content = onExport)
            }
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                minLines = 3,
                label = { Text("…or paste plugin JSON") },
                placeholder = { Text("{ \"sources\": [...], \"themes\": [...] }") },
                modifier = Modifier.fillMaxWidth()
            )
            if (importStatus != null) {
                StatusText(importStatus, importStatus.startsWith("✓"))
            }
            Button(
                onClick = { onImport(pasted); pasted = "" },
                enabled = pasted.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import pasted JSON")
            }
        }
    }
}

@Composable
private fun CsvImportCard(
    status: String?,
    preview: PlaylistImportResult?,
    onImportLink: (String) -> Unit,
    onImportCsv: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var playlistName by remember(preview) { mutableStateOf(preview?.sourceName.orEmpty()) }

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Import a playlist from a CSV file. Only titles and artists are read, then matched against your enabled sources.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var playlistLink by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = playlistLink,
                    onValueChange = { playlistLink = it },
                    singleLine = true,
                    label = { Text("Or paste a playlist link") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { onImportLink(playlistLink); playlistLink = "" }, enabled = playlistLink.isNotBlank()) {
                    Text("Open")
                }
            }
            Text(
                text = "A link is opened by whichever of your switched-on sources recognises it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OpenCsvFileButton(label = "Load CSV file") { text -> onImportCsv(text) }

            if (status != null) {
                StatusText(status, status.startsWith("✓") || status.startsWith("Matched"))
            }

            val importing = preview == null && status != null &&
                (status.startsWith("Matching") || status.startsWith("Reading"))
            if (importing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Matching in the background: you can leave this screen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
            }

            if (preview != null) {
                Text(
                    text = preview.matched.joinToString("\n") { "• ${it.title} - ${it.artist}" }
                        .ifBlank { "No matches found." },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    singleLine = true,
                    label = { Text("Playlist name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onConfirm(playlistName.trim()) },
                        enabled = preview.matched.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create playlist")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String, ok: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

@Composable
private fun SyncToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (enabled) subtitle else "Set up your server above to enable syncing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        }
    }
}

@Composable
private fun ExtensionSettingsDialog(
    title: String,
    settings: dev.spinet.extensions.ExtensionSettingsUi,
    onDismiss: () -> Unit,
    onSave: (Map<String, String>) -> Unit
) {
    val values = remember(settings) {
        androidx.compose.runtime.mutableStateMapOf<String, String>().apply { putAll(settings.values) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.fields.forEach { field ->
                    OutlinedTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it },
                        singleLine = true,
                        label = { Text(field.label) },
                        placeholder = { if (field.hint.isNotBlank()) Text(field.hint) },
                        visualTransformation = if (field.secret) {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Text(
                    "Kept on this device and given only to this source.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSave(values.toMap()) }) { Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun UploadFolderCard(
    defaultDir: String,
    serverFolders: List<String>,
    foldersLoading: Boolean,
    onLoadFolders: () -> Unit,
    onPickFolder: (String) -> Unit
) {
    var dirDraft by remember(defaultDir) { mutableStateOf(defaultDir) }
    var foldersMenu by remember { mutableStateOf(false) }
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Default upload folder",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    dirDraft,
                    { dirDraft = it; onPickFolder(it.trim()) },
                    singleLine = true,
                    label = { Text("Folder (blank = root)") },
                    modifier = Modifier.weight(1f)
                )
                Box {
                    OutlinedButton(onClick = { onLoadFolders(); foldersMenu = true }) {
                        Text(if (foldersLoading) "…" else "Browse")
                    }
                    DropdownMenu(
                        expanded = foldersMenu && serverFolders.isNotEmpty(),
                        onDismissRequest = { foldersMenu = false }
                    ) {
                        serverFolders.forEach { folder ->
                            DropdownMenuItem(
                                text = { Text(folder) },
                                onClick = {
                                    dirDraft = folder
                                    onPickFolder(folder)
                                    foldersMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerKindOption(selected: Boolean, title: String, subtitle: String, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogCard() {
    val label = dev.spinet.util.logButtonLabel ?: return
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Something not working?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Spinet keeps a log of what it does. If you report a problem on GitHub, attach the log so it can be fixed faster. Server addresses, links and personal details are left out of it automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { dev.spinet.util.openAppLog() }, modifier = Modifier.fillMaxWidth()) {
                Text(label)
            }
            dev.spinet.util.saveLogLabel?.let { saveLabel ->
                var saved by remember { mutableStateOf<String?>(null) }
                OutlinedButton(onClick = { saved = dev.spinet.util.saveAppLog() }, modifier = Modifier.fillMaxWidth()) {
                    Text(saveLabel)
                }
                saved?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
