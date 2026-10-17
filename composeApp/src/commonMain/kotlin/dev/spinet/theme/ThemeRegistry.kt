package dev.spinet.theme

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.spinet.model.ThemeSpec
import dev.spinet.settings.SettingsRepository

class ThemeRegistry(
    private val settings: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private val builtIns = listOf(
        ThemeSpec("spinet", "Spinet", "#FF8510", "#FFB020", "#FF5A00", "#000000", dark = true, builtIn = true),

        ThemeSpec("default", "Material Light", "#6750A4", "#625B71", "#7D5260", "#FFFBFE", dark = false, builtIn = true),
        ThemeSpec("midnight", "Midnight", "#8AB4F8", "#81C995", "#C58AF9", "#101014", dark = true, builtIn = true),
        ThemeSpec("sunset", "Sunset", "#FF7043", "#FFB74D", "#BA68C8", "#1A1113", dark = true, builtIn = true),
        ThemeSpec("forest", "Forest", "#4CAF50", "#8D6E63", "#26A69A", "#F1F8E9", dark = false, builtIn = true)
    )

    private var themes: MutableList<ThemeSpec> = load().toMutableList()
    private var selectedId: String =
        settings.selectedThemeId.ifBlank { builtIns.first().id }

    fun all(): List<ThemeSpec> = themes.toList()

    fun selectedId(): String = selectedId

    fun selected(): ThemeSpec =
        themes.firstOrNull { it.id == selectedId } ?: builtIns.first()

    fun select(id: String) {
        if (themes.any { it.id == id }) {
            selectedId = id
            settings.selectedThemeId = id
        }
    }

    fun install(
        name: String,
        primary: String,
        secondary: String,
        tertiary: String,
        background: String,
        dark: Boolean
    ): ThemeSpec? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null
        val colors = listOf(primary, secondary, tertiary, background).map { it.trim() }
        if (colors.any { it.toColorOrNull() == null }) return null

        val theme = ThemeSpec(
            id = "custom_${(cleanName + colors.joinToString()).hashCode()}",
            name = cleanName,
            primary = colors[0],
            secondary = colors[1],
            tertiary = colors[2],
            background = colors[3],
            dark = dark,
            builtIn = false
        )
        themes = themes.filterNot { it.id == theme.id }.toMutableList().apply { add(theme) }
        persist()
        return theme
    }

    fun uninstall(id: String) {
        themes = themes.filterNot { it.id == id && !it.builtIn }.toMutableList()
        if (themes.none { it.id == selectedId }) {
            selectedId = builtIns.first().id
            settings.selectedThemeId = selectedId
        }
        persist()
    }

    private fun load(): List<ThemeSpec> {
        val stored = settings.customThemesJson
        val customs: List<ThemeSpec> = if (stored.isBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(stored)
            } catch (_: Exception) {
                emptyList()
            }
        }
        return builtIns + customs.filter { !it.builtIn }
    }

    private fun persist() {
        settings.customThemesJson = json.encodeToString(themes.filterNot { it.builtIn })
    }
}
