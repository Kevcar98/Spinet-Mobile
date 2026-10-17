package dev.spinet.data

import kotlinx.serialization.Serializable

@Serializable
data class PluginBundle(
    val sources: List<ImportedSource> = emptyList(),
    val themes: List<ImportedTheme> = emptyList()
)

@Serializable
data class ImportedSource(
    val name: String,
    val baseUrl: String
)

@Serializable
data class ImportedTheme(
    val name: String,
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val background: String,
    val dark: Boolean = false
)
