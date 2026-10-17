package dev.spinet.widget

import dev.spinet.library.LibraryRepository
import dev.spinet.model.Track
import dev.spinet.model.matchKey
import dev.spinet.settings.SettingsRepository

internal object WidgetData {
    fun settings(): SettingsRepository = SettingsRepository()

    fun snapshot(): WidgetSnapshot = WidgetSnapshotStore.read(settings())

    fun localCopyOf(key: String): Track? {
        if (key.isBlank()) return null
        val library = LibraryRepository(settings())
        return (library.favorites() + library.playlists().flatMap { it.tracks })
            .firstOrNull { it.matchKey() == key && it.thumbnailUrl.isNotBlank() }
    }
}
