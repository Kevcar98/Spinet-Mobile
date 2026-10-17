package dev.spinet.extensions

import dev.spinet.extension.MusicSource

const val EXTENSION_PLUGIN_PREFIX = "ext:"

data class RejectedExtension(val name: String, val reason: String)

data class ExtensionScan(
    val sources: List<MusicSource> = emptyList(),
    val rejected: List<RejectedExtension> = emptyList(),

    val origins: Map<String, String> = emptyMap()
)

expect fun scanExtensions(): ExtensionScan

fun ExtensionScan.withoutDuplicateIds(): ExtensionScan {
    val seen = mutableSetOf<String>()
    val kept = mutableListOf<MusicSource>()
    val dropped = mutableListOf<RejectedExtension>()
    for (source in sources) {
        val id = runCatching { source.id }.getOrNull()
        when {
            id.isNullOrBlank() -> dropped += RejectedExtension(source::class.simpleName ?: "?", "has no id")
            id.startsWith("dev.spinet.builtin") ->
                dropped += RejectedExtension(id, "uses an id reserved for the app's own sources")
            !seen.add(id) -> dropped += RejectedExtension(id, "another installed extension already uses this id")
            else -> kept += source
        }
    }
    return ExtensionScan(kept, rejected + dropped, origins)
}

data class ExtensionSettingsUi(
    val fields: List<dev.spinet.extension.SettingField>,
    val values: Map<String, String>
)
