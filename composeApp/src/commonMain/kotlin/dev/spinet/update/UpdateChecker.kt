package dev.spinet.update

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.spinet.APP_VERSION

class UpdateChecker(
    private val client: HttpClient,
    private val repo: String,
    private val currentVersion: String = APP_VERSION
) {
    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String = "",
        val name: String = "",
        val body: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val assets: List<Asset> = emptyList()
    )

    @Serializable
    private data class Asset(
        val name: String = "",
        @SerialName("browser_download_url") val url: String = "",
        val size: Long = 0
    )

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    var lastError: String? = null
        private set

    suspend fun check(assetMatcher: (String) -> Boolean): AvailableUpdate? = runCatching {
        lastError = null
        val text = client.get("https://api.github.com/repos/$repo/releases/latest") {
            header("Accept", "application/vnd.github+json")
        }.bodyAsText()
        val release = json.decodeFromString<Release>(text)
        if (release.draft || release.prerelease) return null

        val latest = release.tagName.trimStart('v', 'V')
        if (!isNewer(latest, currentVersion)) return null

        val asset = release.assets.firstOrNull { assetMatcher(it.name) } ?: return null
        if (!isTrustedHost(asset.url)) return null

        AvailableUpdate(
            version = latest,
            notes = release.body.trim(),
            downloadUrl = asset.url,
            downloadName = asset.name,
            sizeBytes = asset.size,

            checksumsUrl = release.assets
                .firstOrNull { it.name.equals("SHA256SUMS", ignoreCase = true) }
                ?.url
                ?.takeIf { isTrustedHost(it) }
        )
    }.getOrElse { e ->

        lastError = e.message ?: e::class.simpleName
        null
    }

    private fun isTrustedHost(url: String): Boolean {
        if (!url.startsWith("https://", ignoreCase = true)) return false
        val host = url.removePrefix("https://").substringBefore('/').lowercase()
        return host == "github.com" ||
            host == "api.github.com" ||
            host.endsWith(".githubusercontent.com")
    }

    internal fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().split('.', '-', '+')
            .mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
        val a = parts(candidate)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

data class AvailableUpdate(
    val version: String,
    val notes: String,
    val downloadUrl: String,
    val downloadName: String,
    val sizeBytes: Long,
    val checksumsUrl: String?
)
