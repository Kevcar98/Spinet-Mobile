package dev.spinet.widget

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.spinet.data.RefusedSignIn
import dev.spinet.data.createPlatformHttpClient
import dev.spinet.data.savedServerSignIns
import dev.spinet.data.savedServers
import dev.spinet.data.serverSignInPlugin
import dev.spinet.model.NowPlayingState
import dev.spinet.settings.SettingsRepository

internal object WidgetServer {
    private val json = Json { ignoreUnknownKeys = true }

    private fun baseUrl(settings: SettingsRepository): String? =
        settings.savedServers().firstOrNull { it.enabled }?.baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }

    suspend fun bookmark(
        settings: SettingsRepository,
        compact: Boolean = false
    ): NowPlayingState? {
        if (!settings.syncPlaybackEnabled) {
            WidgetLog.d("server: cross-device resume is switched off, staying local")
            return null
        }
        val base = baseUrl(settings)
        if (base == null) {
            WidgetLog.d("server: no host configured, staying local")
            return null
        }

        var result: NowPlayingState? = null
        repeat(ATTEMPTS) { attempt ->
            if (result != null) return@repeat
            if (attempt > 0) WidgetLog.d("server: retry ${attempt + 1}/$ATTEMPTS")
            result = fetchOnce(base, compact, settings)
        }

        if (result != null) {
            runCatching { settings.widgetServerBookmarkJson = json.encodeToString(result) }
            return result
        }
        WidgetLog.d("server: no bookmark from $base (unreachable, slow, or none set)")
        val cached = runCatching {
            settings.widgetServerBookmarkJson.takeIf { it.isNotBlank() }
                ?.let { json.decodeFromString<NowPlayingState>(it) }
        }.getOrNull()
        if (cached != null) {
            WidgetLog.d("server: falling back to the last bookmark it gave us ('${cached.device}')")
        }
        return cached
    }

    private val refusedSignIn = RefusedSignIn()

    private suspend fun fetchOnce(base: String, compact: Boolean, settings: SettingsRepository): NowPlayingState? =
        withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                runCatching {
                    val plain = createPlatformHttpClient()
                    val client = plain.config {
                        install(serverSignInPlugin({ settings.savedServerSignIns() }, refusedSignIn))
                    }
                    try {
                        val url = if (compact) "$base/nowplaying?compact=1" else "$base/nowplaying"
                        val text: String = client.get(url).bodyAsText()
                        WidgetLog.d("server: GET $url -> ${text.length} bytes")

                        json.decodeFromString<NowPlayingState>(text)
                    } finally {
                        client.close()
                        plain.close()
                    }
                }.onFailure {
                    WidgetLog.d("server: request/parse failed: ${it::class.simpleName}: ${it.message}")
                }.getOrNull()
            }
        }

    private const val ATTEMPTS = 2

    private const val ATTEMPT_TIMEOUT_MS = 3_000L
}
