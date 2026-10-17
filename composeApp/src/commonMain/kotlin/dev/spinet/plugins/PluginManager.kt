package dev.spinet.plugins

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import dev.spinet.extensions.EXTENSION_PLUGIN_PREFIX
import dev.spinet.extensions.ExtensionScan
import dev.spinet.extensions.ExtensionSettingsUi
import dev.spinet.extensions.scanExtensions
import dev.spinet.model.SourceKind
import dev.spinet.model.SourcePlugin
import dev.spinet.settings.SettingsRepository

class PluginManager(
    private val settings: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val builtIns = emptyList<SourcePlugin>()

    private var plugins: MutableList<SourcePlugin> = load().toMutableList()

    @kotlin.concurrent.Volatile
    private var scan: ExtensionScan? = null

    val extensions: ExtensionScan get() = scan ?: scanExtensions().also { scan = it; applyAllSettings(it) }

    fun extensionOrigin(pluginId: String): String? = when {
        pluginId.startsWith(EXTENSION_PLUGIN_PREFIX) ->
            extensions.origins[pluginId.removePrefix(EXTENSION_PLUGIN_PREFIX)]
        pluginId.startsWith(REJECTED_PREFIX) -> pluginId.removePrefix(REJECTED_PREFIX)
        else -> null
    }

    fun rescanExtensions() {
        scan = scanExtensions().also { applyAllSettings(it) }
    }

    private val settingsSerializer = kotlinx.serialization.serializer<Map<String, Map<String, String>>>()

    private fun storedSettings(): Map<String, Map<String, String>> =
        runCatching { json.decodeFromString(settingsSerializer, settings.extensionSettingsJson) }
            .getOrDefault(emptyMap())

    private fun applyAllSettings(scan: ExtensionScan) {
        val stored = storedSettings()
        for (source in scan.sources) {
            runCatching { source.applySettings(stored[source.id].orEmpty()) }
        }
    }

    fun extensionSettings(pluginId: String): ExtensionSettingsUi? {
        if (!pluginId.startsWith(EXTENSION_PLUGIN_PREFIX)) return null
        val id = pluginId.removePrefix(EXTENSION_PLUGIN_PREFIX)
        val source = extensions.sources.firstOrNull { it.id == id } ?: return null
        val fields = runCatching { source.settings }.getOrDefault(emptyList())
        if (fields.isEmpty()) return null
        return ExtensionSettingsUi(fields, storedSettings()[id].orEmpty())
    }

    fun saveExtensionSettings(pluginId: String, values: Map<String, String>) {
        val id = pluginId.removePrefix(EXTENSION_PLUGIN_PREFIX)
        val source = extensions.sources.firstOrNull { it.id == id } ?: return
        val clean = values.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        settings.extensionSettingsJson = json.encodeToString(settingsSerializer, storedSettings() + (id to clean))
        runCatching { source.applySettings(clean) }
    }

    private var enabledExtensionIds: Set<String> =
        settings.enabledExtensionIds.lines().filter { it.isNotBlank() }.toSet()

    private fun extensionRows(): List<SourcePlugin> =
        extensions.sources.map { source ->
            SourcePlugin(
                id = EXTENSION_PLUGIN_PREFIX + source.id,
                name = source.name,
                kind = SourceKind.EXTENSION,
                baseUrl = "Extension · version ${source.versionCode}",
                enabled = source.id in enabledExtensionIds,
                builtIn = true
            )
        } + extensions.rejected.map { rejected ->

            SourcePlugin(
                id = REJECTED_PREFIX + rejected.name,
                name = rejected.name,
                kind = SourceKind.EXTENSION,
                baseUrl = "Can't load: ${rejected.reason}",
                enabled = false,
                builtIn = true
            )
        }

    fun all(): List<SourcePlugin> = plugins.toList() + extensionRows()

    fun enabledOrdered(): List<SourcePlugin> {
        val enabled = (plugins + extensionRows()).filter { it.enabled }
        return enabled.filter { it.kind == SourceKind.SERVER } +
            enabled.filter { it.kind == SourceKind.EXTENSION }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        if (id.startsWith(EXTENSION_PLUGIN_PREFIX)) {
            val sourceId = id.removePrefix(EXTENSION_PLUGIN_PREFIX)
            if (extensions.sources.none { it.id == sourceId }) return
            enabledExtensionIds = if (enabled) enabledExtensionIds + sourceId else enabledExtensionIds - sourceId
            settings.enabledExtensionIds = enabledExtensionIds.joinToString("\n")
            return
        }
        plugins = plugins.map { if (it.id == id) it.copy(enabled = enabled) else it }.toMutableList()
        persist()
    }

    fun install(name: String, baseUrl: String): SourcePlugin? {
        val cleanName = name.trim()
        val cleanUrl = baseUrl.trim().trimEnd('/')
        if (cleanName.isBlank() || cleanUrl.isBlank()) return null
        val plugin = SourcePlugin(
            id = "custom_${cleanUrl.hashCode()}",
            name = cleanName,
            kind = SourceKind.SERVER,
            baseUrl = cleanUrl,
            enabled = true,
            builtIn = false
        )
        putInPlace(plugin)
        return plugin
    }

    fun rename(id: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        plugins = plugins.map { if (it.id == id) it.copy(name = clean) else it }.toMutableList()
        persist()
    }

    fun update(id: String, newName: String, newBaseUrl: String) {
        val cleanName = newName.trim()
        val cleanUrl = newBaseUrl.trim().trimEnd('/')
        if (cleanName.isBlank() || cleanUrl.isBlank()) return
        plugins = plugins.map {
            if (it.id == id) it.copy(name = cleanName, baseUrl = cleanUrl) else it
        }.toMutableList()
        persist()
    }

    fun upsert(id: String, name: String, baseUrl: String): SourcePlugin {
        val clean = baseUrl.trim().trimEnd('/')
        val plugin = SourcePlugin(
            id = id,
            name = name.trim().ifBlank { name },
            kind = SourceKind.SERVER,
            baseUrl = clean,
            enabled = true,
            builtIn = false
        )
        putInPlace(plugin)
        return plugin
    }

    private fun putInPlace(plugin: SourcePlugin) {
        val at = plugins.indexOfFirst { it.id == plugin.id }
        if (at >= 0) plugins[at] = plugin else plugins.add(plugin)
        persist()
    }

    fun servers(): List<SourcePlugin> = plugins.filter { it.kind == SourceKind.SERVER }

    fun moveServer(id: String, up: Boolean) {
        val servers = servers()
        val from = servers.indexOfFirst { it.id == id }
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in servers.indices) return
        val a = plugins.indexOf(servers[from])
        val b = plugins.indexOf(servers[to])
        plugins[a] = servers[to]
        plugins[b] = servers[from]
        persist()
    }

    fun uninstall(id: String) {
        plugins = plugins.filterNot { it.id == id && !it.builtIn }.toMutableList()
        persist()
    }

    private fun load(): List<SourcePlugin> {
        val stored = settings.sourcePluginsJson
        val restored: List<SourcePlugin> = if (stored.isBlank()) {
            builtIns
        } else {
            runCatching {
                json.parseToJsonElement(stored).jsonArray.mapNotNull { row ->
                    runCatching { json.decodeFromJsonElement(SourcePlugin.serializer(), row) }.getOrNull()
                }
            }.getOrDefault(builtIns)
        }

        val mergedBuiltIns = builtIns.map { b -> restored.firstOrNull { it.id == b.id } ?: b }
        val customs = restored.filter { !it.builtIn }
        return mergedBuiltIns + customs
    }

    private fun persist() {
        settings.sourcePluginsJson = json.encodeToString(plugins.toList())
    }

    private companion object {
        const val REJECTED_PREFIX = "ext-rejected:"
    }
}
