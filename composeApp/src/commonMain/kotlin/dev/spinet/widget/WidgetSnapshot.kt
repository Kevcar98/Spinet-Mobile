package dev.spinet.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.spinet.settings.SettingsRepository

@Serializable
data class WidgetSnapshot(
    val title: String = "",
    val artist: String = "",

    val artUrl: String = "",

    val trackKey: String = "",
    val isPlaying: Boolean = false,

    val hasSession: Boolean = false,

    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L
) {
    val progress: Float?
        get() = if (durationMs <= 0L) null
        else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

object WidgetSnapshotStore {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(settings: SettingsRepository): WidgetSnapshot {
        val raw = settings.widgetSnapshotJson
        if (raw.isBlank()) return WidgetSnapshot()
        return runCatching { json.decodeFromString<WidgetSnapshot>(raw) }
            .getOrDefault(WidgetSnapshot())
    }

    fun write(settings: SettingsRepository, snapshot: WidgetSnapshot) {
        runCatching { settings.widgetSnapshotJson = json.encodeToString(snapshot) }
    }
}
