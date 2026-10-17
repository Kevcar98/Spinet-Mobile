package dev.spinet.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import dev.spinet.extension.Capability
import dev.spinet.extension.MusicSource
import dev.spinet.extension.SourceTrack
import dev.spinet.extensions.EXTENSION_PLUGIN_PREFIX
import dev.spinet.model.SourceKind
import dev.spinet.model.SourcePlugin
import dev.spinet.model.NowPlayingState
import dev.spinet.model.PlaylistSyncState
import dev.spinet.model.Track
import dev.spinet.model.TrackSource
import dev.spinet.model.SERVER_TRACK_ID_PREFIX

private val COVER_MARKERS = listOf(
    "originallyperformedby", "karaoke", "madefamousby", "inthestyleof",
    "tributeto", "tributeversion", "coverversion", "instrumentalversion",
    "backingtrack"
)

class SearchUnavailableException(
    message: String,
    cause: Throwable?
) : Exception(message, cause)

class NetworkRepository(
    client: HttpClient,

    private val sourcesProvider: () -> List<SourcePlugin> = { emptyList() },

    private val extensionSources: () -> List<MusicSource> = { emptyList() },

    private val serverSignIns: () -> List<ServerSignIn> = { emptyList() }
) {
    private val refusedSignIn = RefusedSignIn()
    private val client: HttpClient = client.config { install(serverSignInPlugin(serverSignIns, refusedSignIn)) }

    suspend fun checkServerAccess(base: String): ServerAccess {
        refusedSignIn.forget()
        val root = base.trim().trimEnd('/')

        suspend fun fetch(path: String): HttpResponse? = try {
            client.get("$root$path")
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            e.response
        } catch (e: Exception) {
            null
        }
        val health = fetch("/health") ?: return ServerAccess.UNREACHABLE
        val healthText = runCatching { health.bodyAsText() }.getOrDefault("")
        if (!health.status.isSuccess() || "\"status\"" !in healthText) return ServerAccess.UNREACHABLE

        val signInOn = healthText.replace(" ", "").contains("\"auth\":true")
        val listing = fetch("/playlists") ?: return ServerAccess.UNREACHABLE
        return when {
            listing.status.isSuccess() -> if (signInOn) ServerAccess.SIGNED_IN else ServerAccess.OPEN
            listing.status == HttpStatusCode.Unauthorized ->
                if (serverSignIns().any { it.isSet && it.baseUrl.trimEnd('/') == root }) ServerAccess.WRONG_SIGN_IN
                else ServerAccess.NEEDS_SIGN_IN
            listing.status == HttpStatusCode.TooManyRequests -> ServerAccess.LOCKED_OUT
            else -> ServerAccess.UNREACHABLE
        }
    }

    val httpClient: HttpClient get() = client

    private fun enabledExtensions(): List<MusicSource> =
        sourcesProvider()
            .filter { it.kind == SourceKind.EXTENSION }
            .mapNotNull { extensionFor(it.id) }

    private fun extensionFor(pluginId: String): MusicSource? {
        val sourceId = pluginId.removePrefix(EXTENSION_PLUGIN_PREFIX)
        return extensionSources().firstOrNull { it.id == sourceId }
    }

    private fun SourceTrack.toExtensionTrack(source: MusicSource) = Track(
        id = "ext:${source.id}:$id",
        title = title,
        artist = artist.ifBlank { "Unknown artist" },
        durationLabel = formatDuration(durationMs / 1000),
        thumbnailUrl = artworkUrl,
        source = TrackSource.ONLINE,
        itemId = id,
        streamUrl = "",
        downloadable = Capability.DOWNLOAD in source.capabilities,
        serverBaseUrl = "",
        sourceId = source.id
    )

    @kotlinx.serialization.Serializable
    private data class MoveRequest(
        @kotlinx.serialization.SerialName("track_id") val trackId: String,
        val folder: String = ""
    )

    suspend fun moveOnServer(serverBaseUrl: String, trackId: String, folder: String): Boolean =
        runCatching {
            client.post("${serverBaseUrl.trimEnd('/')}/move") {
                contentType(ContentType.Application.Json)
                setBody(MoveRequest(trackId, folder))
            }.status.value in 200..299
        }.getOrDefault(false)

    private val itunesJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    @kotlinx.serialization.Serializable
    private data class ItunesSearchResponse(val results: List<ItunesResult> = emptyList())

    @kotlinx.serialization.Serializable
    private data class ItunesResult(
        val artworkUrl100: String? = null,
        val trackName: String? = null,
        val artistName: String? = null,
        val artistId: Long? = null,

        val wrapperType: String? = null
    )

    data class TrackInfo(val title: String, val artist: String, val artworkUrl: String)

    private val itunesArtistIds = mutableMapOf<String, Long?>()

    private suspend fun artistCatalogue(artist: String): List<ItunesResult> {
        val name = artist.split(",", "&", ";").first().trim()
        if (name.isBlank()) return emptyList()
        return try {
            val id = itunesArtistIds.getOrPut(name) {
                val text = client.get("https://itunes.apple.com/search") {
                    parameter("term", name)
                    parameter("entity", "musicArtist")
                    parameter("limit", "1")
                }.bodyAsText()
                itunesJson.decodeFromString(ItunesSearchResponse.serializer(), text)
                    .results.firstOrNull()?.artistId
            } ?: return emptyList()
            val text = client.get("https://itunes.apple.com/lookup") {
                parameter("id", id.toString())
                parameter("entity", "song")
                parameter("limit", "200")
            }.bodyAsText()
            itunesJson.decodeFromString(ItunesSearchResponse.serializer(), text)
                .results.filter { it.wrapperType == "track" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun lookupTrackInfo(artist: String, title: String): TrackInfo? {
        val term = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (term.isBlank()) return null
        return try {
            val text = client.get("https://itunes.apple.com/search") {
                parameter("term", term)
                parameter("entity", "song")
                parameter("limit", "5")
            }.bodyAsText()
            val results = itunesJson.decodeFromString(ItunesSearchResponse.serializer(), text).results
            fun norm(s: String?) = s.orEmpty().lowercase().filter { it.isLetterOrDigit() }
            val wantTitle = norm(title)
            val wantArtist = norm(artist.split(",", "&", ";").first())

            fun titleOk(t: String) = t.isNotBlank() &&
                (t == wantTitle || t.contains(wantTitle) || wantTitle.contains(t))

            fun isCover(t: String) = COVER_MARKERS.any { it in t }

            val exact = results.firstOrNull { hit ->
                val t = norm(hit.trackName)
                val a = norm(hit.artistName)
                titleOk(t) && !isCover(t) && (
                    wantArtist.isBlank() ||
                        (a.isNotBlank() && (a.contains(wantArtist) || wantArtist.contains(a)))
                )
            }

            val r = exact
                ?: results.firstOrNull { hit ->
                    val t = norm(hit.trackName)
                    wantArtist.isNotBlank() && titleOk(t) && !isCover(t) && t.contains(wantArtist)
                }
                ?: artistCatalogue(artist).firstOrNull { hit ->
                    val t = norm(hit.trackName)
                    titleOk(t) && !isCover(t)
                }
                ?: return null
            TrackInfo(
                title = r.trackName.orEmpty(),
                artist = r.artistName.orEmpty(),

                artworkUrl = r.artworkUrl100?.replace("100x100bb", "600x600bb").orEmpty()
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(query: String, musicOnly: Boolean = true): List<Track> {
        if (query.isBlank()) return emptyList()
        val serverFilter = if (musicOnly) "music_songs" else "videos"

        val enabledSources = sourcesProvider()
        if (enabledSources.isEmpty()) {
            throw SearchUnavailableException(
                "No sources enabled. Turn one on in Settings > Sources.",
                null
            )
        }

        val merged = mutableListOf<Track>()
        var anySucceeded = false
        var lastError: Throwable? = null

        for (source in enabledSources) {
            try {
                when (source.kind) {
                    SourceKind.SERVER -> {
                        merged += searchServer(
                            source.baseUrl,
                            query,
                            downloadable = true,
                            serverBaseUrl = source.baseUrl,
                            filter = serverFilter
                        )
                        anySucceeded = true
                    }

                    SourceKind.EXTENSION -> {
                        val ext = extensionFor(source.id)
                        if (ext != null && Capability.SEARCH in ext.capabilities) {
                            val found = if (musicOnly) ext.search(query, 1) else ext.searchVersions(query)
                            merged += found.map { it.toExtensionTrack(ext) }
                            anySucceeded = true
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (!anySucceeded) {
            throw SearchUnavailableException(
                "Search is temporarily unavailable: all sources are busy or offline. " +
                    "Please try again later.",
                lastError
            )
        }
        return rankByRelevance(query, merged)
    }

    private fun rankByRelevance(query: String, tracks: List<Track>): List<Track> {
        val qTokens = tokenize(query)
        if (qTokens.isEmpty() || tracks.size < 2) return tracks
        return tracks.withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<Track>> { relevanceScore(qTokens, it.value) }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private fun tokenize(s: String): List<String> =
        s.lowercase()
            .replace(Regex("[\\[\\](){}]"), " ")
            .replace(Regex("\\b(feat|ft|prod)\\.?", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("[&,\\-|/\"'’]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }

    private val junkMarkers = listOf(
        "karaoke", "made popular", "originally performed", "in the style of",
        "tribute", "instrumental", "reprise", "cover version", "8d audio", "sped up",
    )

    private fun relevanceScore(qTokens: List<String>, t: Track): Double {
        val hay = tokenize("${t.artist} ${t.title}").toHashSet()
        if (hay.isEmpty()) return 0.0
        val matched = qTokens.count { it in hay }
        var score = matched.toDouble() / qTokens.size
        val text = "${t.artist} ${t.title}".lowercase()

        val wantsJunk = qTokens.any { it in "karaoke instrumental cover tribute" }
        if (!wantsJunk && junkMarkers.any { it in text }) score -= 0.5
        return score
    }

    fun canRecognize(): Boolean = enabledExtensions().any { runCatching { it.canRecognize }.getOrDefault(false) }

    suspend fun recognizeSong(pcm16kMono: ShortArray): dev.spinet.recognition.RecognizedSong? {
        val recognizers = enabledExtensions().filter { runCatching { it.canRecognize }.getOrDefault(false) }
        var failure: Throwable? = null
        for (ext in recognizers) {
            try {
                val hit = ext.recognize(pcm16kMono, 16_000) ?: continue
                return dev.spinet.recognition.RecognizedSong(hit.title, hit.artist, hit.artworkUrl.ifBlank { null })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        if (failure != null && recognizers.size == 1) throw failure
        return null
    }

    fun isLink(input: String): Boolean {
        val t = input.trim()
        return t.startsWith("https://", ignoreCase = true) || t.startsWith("http://", ignoreCase = true)
    }

    suspend fun openLink(input: String): Track? {
        if (!isLink(input)) return null
        for (ext in enabledExtensions()) {
            val found = try {
                ext.open(input.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (found != null) return found.toExtensionTrack(ext)
        }
        return null
    }

    suspend fun browsePlaylists(baseUrl: String): List<BrowsePlaylistDto> {
        return try {
            browsePlaylistsOrThrow(baseUrl)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun browsePlaylistsOrThrow(baseUrl: String): List<BrowsePlaylistDto> =
        client.get("${baseUrl.trimEnd('/')}/playlists")
            .body<BrowsePlaylistsResponse>()
            .playlists

    suspend fun listServerFolders(baseUrl: String): List<String> {
        val base = baseUrl.trim().trimEnd('/')
        return try {
            client.get("$base/playlists")
                .body<BrowsePlaylistsResponse>()
                .playlists
                .map { it.name }
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            throw SearchUnavailableException(
                "Couldn't reach the library server at $base: ${e.message ?: e}", e
            )
        }
    }

    suspend fun browsePlaylistTracks(baseUrl: String, playlistId: String): List<Track> {
        val response: ServerSearchResponse = client.get("$baseUrl/playlists/$playlistId").body()

        return response.items.mapNotNull { it.toTrackOrNull(downloadable = true, serverBaseUrl = baseUrl) }
    }

    suspend fun searchLibrary(baseUrl: String, query: String): List<Track> {
        val response: ServerSearchResponse = client.get("${baseUrl.trimEnd('/')}/search") {
            parameter("q", query)
        }.body()
        return response.items.mapNotNull { it.toTrackOrNull(downloadable = true, serverBaseUrl = baseUrl) }
    }

    suspend fun fetchServerTransfers(baseUrl: String, limit: Int = 0): List<ServerTransferDto> =
        runCatching {
            val resp: ServerTransfersResponse =
                client.get("${baseUrl.trimEnd('/')}/transfers?limit=$limit").body()
            resp.transfers.reversed()
        }.getOrDefault(emptyList())

    suspend fun getNowPlaying(
        baseUrl: String,
        withHistory: Boolean = false
    ): NowPlayingState? = runCatching {
        val url = "${baseUrl.trimEnd('/')}/nowplaying" + if (withHistory) "?history=1" else ""
        val resp: NowPlayingState = client.get(url).body()
        resp.takeIf { it.updatedAt > 0 }
    }.getOrNull()

    suspend fun putNowPlaying(baseUrl: String, state: NowPlayingState) {
        runCatching {
            client.put("${baseUrl.trimEnd('/')}/nowplaying") {
                contentType(ContentType.Application.Json)
                setBody(state)
            }
        }
    }

    suspend fun getPlaylistSync(baseUrl: String): PlaylistSyncState? = runCatching {
        val resp: PlaylistSyncState = client.get("${baseUrl.trimEnd('/')}/syncplaylists").body()
        resp.takeIf { it.updatedAt > 0 }
    }.getOrNull()

    suspend fun putPlaylistSync(baseUrl: String, state: PlaylistSyncState): Boolean = runCatching {
        client.put("${baseUrl.trimEnd('/')}/syncplaylists") {
            contentType(ContentType.Application.Json)
            setBody(state)
        }.status.isSuccess()
    }.getOrDefault(false)

    suspend fun existsOnServer(serverBaseUrl: String, title: String, artist: String): Boolean {
        val base = serverBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return false
        val query = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (query.isBlank()) return false
        val response: ServerSearchResponse = try {
            client.get("$base/search") {
                parameter("q", query)
                parameter("filter", "music_songs")
            }.body()
        } catch (e: Exception) {
            return false
        }

        fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        fun artistKey(s: String) = norm(s).takeIf { it.isNotBlank() && it != "unknownartist" }
        val wantedTitle = norm(title)
        val wantedArtist = artistKey(artist)
        if (wantedTitle.isBlank()) return false

        return response.items.any { item ->
            val itTitle = norm(item.title.orEmpty())
            val itArtist = artistKey(item.uploaderName.orEmpty())
            val titleMatch = itTitle == wantedTitle

            val artistMatch = wantedArtist == null || itArtist == null || itArtist == wantedArtist
            val complete = item.size == null || item.size >= MIN_COMPLETE_BYTES
            titleMatch && artistMatch && complete
        }
    }

    suspend fun rescanServer(serverBaseUrl: String) {
        val base = serverBaseUrl.trim().trimEnd('/')
        if (base.isBlank()) return
        runCatching { client.post("$base/rescan") }
    }

    suspend fun deleteFromServer(serverBaseUrl: String, trackId: String): Boolean {
        val base = serverBaseUrl.trim().trimEnd('/')
        if (base.isBlank() || trackId.isBlank()) return false
        val response = try {
            client.delete("$base/file/$trackId")
        } catch (e: Exception) {
            throw SearchUnavailableException("Couldn't reach the server: ${e.message ?: e}", e)
        }
        if (!response.status.isSuccess()) {
            throw SearchUnavailableException(
                "Server rejected the delete (HTTP ${response.status.value}). " +
                    "Make sure the library server is restarted with the delete route.",
                null
            )
        }
        return true
    }

    suspend fun uploadFileToServer(
        serverBaseUrl: String,
        filename: String,
        bytes: ByteArray,
        folder: String = "",
        title: String = "",
        artist: String = "",
        artUrl: String = ""
    ): String {
        val base = serverBaseUrl.trim().trimEnd('/')
        val response: ImportResponse = try {
            client.post("$base/upload") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            if (folder.isNotBlank()) append("folder", folder)

                            if (title.isNotBlank()) append("title", title)
                            if (artist.isNotBlank()) append("artist", artist)
                            if (artUrl.startsWith("http", ignoreCase = true)) {
                                append("art_url", artUrl)
                            }
                            append(
                                "file",
                                bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, "audio/mpeg")
                                    append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                                }
                            )
                        }
                    )
                )
            }.body()
        } catch (e: Exception) {
            throw SearchUnavailableException("Upload failed: ${e.message ?: e}", e)
        }
        val saved = response.saved
            ?: throw SearchUnavailableException("Server didn't confirm the upload.", null)

        val sent = bytes.size.toLong()
        val received = response.received
        val truncated = when {
            received != null -> received != sent
            response.size != null -> response.size < sent * 9 / 10
            else -> false
        }
        if (truncated) {
            throw SearchUnavailableException(
                "Upload incomplete (${received ?: response.size}/$sent bytes).", null
            )
        }
        return saved
    }

    suspend fun importCsvPlaylist(
        csvText: String,
        playlistName: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): PlaylistImportResult {
        val refs = parseCsvTrackRefs(csvText)
        if (refs.isEmpty()) {
            throw SearchUnavailableException(
                "No tracks found in that CSV. It needs a header row with a track/title column.",
                null
            )
        }
        return matchExternalTracks(playlistName.ifBlank { "Imported playlist" }, refs, onProgress)
    }

    suspend fun importPlaylistLink(
        url: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): PlaylistImportResult {
        var failure: Exception? = null
        for (ext in enabledExtensions()) {
            val list = try {
                ext.openPlaylist(url.trim())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                null
            } ?: continue
            if (list.playable) {
                return PlaylistImportResult(list.name.ifBlank { "Imported playlist" }, list.tracks.map { it.toExtensionTrack(ext) })
            }
            val refs = list.tracks.map { ExternalTrackRef(it.title, it.artist) }
            return matchExternalTracks(list.name.ifBlank { "Imported playlist" }, refs, onProgress)
        }
        throw SearchUnavailableException(
            failure?.message ?: "None of your switched-on sources can open that link.", failure
        )
    }

    suspend fun retryUnmatchedTracks(
        previous: PlaylistImportResult,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): PlaylistImportResult {
        if (previous.unmatched.isEmpty()) return previous
        val retry = matchExternalTracks(previous.sourceName, previous.unmatched, onProgress)
        return PlaylistImportResult(
            sourceName = previous.sourceName,
            matched = previous.matched + retry.matched,
            unmatched = retry.unmatched
        )
    }

    private suspend fun matchExternalTracks(
        name: String,
        refs: List<ExternalTrackRef>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): PlaylistImportResult {
        val matched = mutableListOf<Track>()
        val unmatched = mutableListOf<ExternalTrackRef>()
        refs.forEachIndexed { index, ref ->

            coroutineContext.ensureActive()
            val query = listOf(ref.artist, ref.title).filter { it.isNotBlank() }.joinToString(" ")
            val hit = try {
                search(query).firstOrNull()
            } catch (e: Exception) {
                null
            }
            if (hit != null) matched += hit else unmatched += ref
            onProgress(index + 1, refs.size)
        }
        return PlaylistImportResult(name, matched, unmatched)
    }

    private fun parseCsvTrackRefs(csvText: String): List<ExternalTrackRef> {
        val rows = parseCsvRows(csvText)
        if (rows.isEmpty()) return emptyList()

        val header = rows.first().map { it.trim().lowercase() }
        fun findCol(vararg needles: String): Int =
            header.indexOfFirst { h -> needles.any { h == it } }
                .takeIf { it >= 0 }
                ?: header.indexOfFirst { h -> needles.any { h.contains(it) } }

        val titleCol = findCol("track name", "track title", "title", "song", "name")
        val artistCol = findCol("artist name(s)", "artist name", "artist(s)", "artists", "artist")
        if (titleCol < 0) return emptyList()

        return rows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(titleCol)?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val rawArtist = if (artistCol >= 0) row.getOrNull(artistCol)?.trim().orEmpty() else ""
            val artist = rawArtist.split(',', ';', '/', '&').first().trim()
            ExternalTrackRef(title, artist)
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder()
        var row = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        val s = text.replace("\r\n", "\n").replace('\r', '\n')
        while (i < s.length) {
            val c = s[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < s.length && s[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> {
                    row.add(field.toString()); field = StringBuilder()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }

        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }

        return rows.filter { r -> r.any { it.isNotBlank() } }
    }

    suspend fun resolveStreamUrl(track: Track): String {
        if (track.source == TrackSource.LOCAL) {
            return track.streamUrl.ifBlank {
                throw SearchUnavailableException("This track has no playable audio.", null)
            }
        }

        if (track.sourceId.isNotBlank()) return resolveFromExtension(track)

        if (track.serverBaseUrl.isNotBlank() && track.itemId.isNotBlank()) {
            serverStream(track.serverBaseUrl, track.itemId)?.let { return it }
        }

        if (track.serverBaseUrl.isBlank() && track.itemId.isNotBlank()) {
            for (ext in enabledExtensions()) {
                try {
                    ext.resolve(track.itemId)?.url?.takeIf { it.isNotBlank() }?.let { return it }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                }
            }
        }

        for (base in serverBases()) {
            serverStream(base, track.itemId)?.let { return it }
        }

        throw SearchUnavailableException(
            if (track.serverBaseUrl.isBlank()) {
                "None of your sources can play this. Install or switch on the one it came from under Sources."
            } else {
                "Couldn't load audio for this track. Please try another."
            },
            null
        )
    }

    private fun serverBases(): List<String> =
        sourcesProvider().filter { it.kind == SourceKind.SERVER }.map { it.baseUrl.trimEnd('/') }

    private fun matchServerScheme(url: String, base: String): String {
        if (!base.startsWith("https://", ignoreCase = true) || !url.startsWith("http://", ignoreCase = true)) return url
        val link = runCatching { Url(url) }.getOrNull() ?: return url
        val server = runCatching { Url(base) }.getOrNull() ?: return url
        if (!link.host.equals(server.host, ignoreCase = true)) return url
        if (link.specifiedPort != 0 && link.specifiedPort != 80) return url
        return "https://" + url.substring("http://".length).replaceFirst("${link.host}:80", link.host)
    }

    private suspend fun serverStream(base: String, id: String): String? = try {
        val response: ServerStreamsResponse = client.get("${base.trimEnd('/')}/streams/$id").body()
        val usable = response.audioStreams.filter { !it.url.isNullOrBlank() }

        val originals = usable.filter {
            it.audioTrackType == null || it.audioTrackType.equals("original", ignoreCase = true)
        }
        originals.ifEmpty { usable }.maxByOrNull { it.bitrate ?: 0 }?.url?.let { matchServerScheme(it, base) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun resolveFromExtension(track: Track): String {
        val pluginId = EXTENSION_PLUGIN_PREFIX + track.sourceId
        val ext = extensionFor(pluginId) ?: throw SearchUnavailableException(
            "\"${track.title}\" comes from a source that isn't installed any more.", null
        )

        if (sourcesProvider().none { it.id == pluginId }) {
            throw SearchUnavailableException(
                "Turn on ${ext.name} in Sources to play \"${track.title}\".", null
            )
        }
        val link = try {
            ext.resolve(track.itemId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw SearchUnavailableException("${ext.name} couldn't load this track: ${e.message ?: e}", e)
        }

        return link?.url?.takeIf { it.isNotBlank() }
            ?: throw SearchUnavailableException("${ext.name} has no audio for this track.", null)
    }

    suspend fun resolveExtensionDownload(track: Track): DownloadableAudio? {
        if (track.serverBaseUrl.isNotBlank() || track.itemId.isBlank()) return null
        val candidates = if (track.sourceId.isNotBlank()) {
            listOfNotNull(
                extensionFor(EXTENSION_PLUGIN_PREFIX + track.sourceId)
                    ?.takeIf { ext -> sourcesProvider().any { it.id == EXTENSION_PLUGIN_PREFIX + ext.id } }
            )
        } else {
            enabledExtensions()
        }
        for (ext in candidates.filter { Capability.DOWNLOAD in it.capabilities }) {
            try {
                val link = ext.resolveForDownload(track.itemId)
                if (link != null && link.downloadable && link.url.isNotBlank()) {
                    return DownloadableAudio(link.url, fileExtensionFor(link.mimeType), link.mimeType.ifBlank { "audio/mp4" })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
        return null
    }

    private fun fileExtensionFor(mimeType: String): String = when (mimeType.substringBefore(';').trim().lowercase()) {
        "audio/webm" -> "webm"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/ogg", "audio/opus" -> "ogg"
        "audio/flac" -> "flac"
        "audio/wav", "audio/x-wav" -> "wav"
        else -> "m4a"
    }

    suspend fun lookupArtwork(artist: String, title: String): String? {
        val term = listOf(artist, title).filter { it.isNotBlank() }.joinToString(" ")
        if (term.isBlank()) return null
        return lookupTrackInfo(artist, title)?.artworkUrl?.ifBlank { null }
    }

    suspend fun downloadBytes(url: String): ByteArray =
        client.get(url) {
            header(
                HttpHeaders.UserAgent,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            )
        }.body()

    suspend fun downloadAudioBytes(url: String): ByteArray {
        val pieces = mutableListOf<ByteArray>()
        var position = 0L
        while (true) {
            val response = try {
                client.get(url) {
                    header(
                        HttpHeaders.UserAgent,
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    )
                    header(HttpHeaders.Range, "bytes=$position-${position + AUDIO_PIECE_BYTES - 1}")
                }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.RequestedRangeNotSatisfiable && position > 0) break
                throw e
            }
            val piece: ByteArray = response.body()
            pieces += piece
            position += piece.size
            if (response.status != HttpStatusCode.PartialContent) break
            val total = response.headers[HttpHeaders.ContentRange]?.substringAfterLast('/')?.toLongOrNull()
            if (piece.size < AUDIO_PIECE_BYTES || (total != null && position >= total)) break
        }
        if (pieces.size == 1) return pieces[0]
        val all = ByteArray(pieces.sumOf { it.size })
        var at = 0
        for (piece in pieces) {
            piece.copyInto(all, at)
            at += piece.size
        }
        return all
    }

    suspend fun testBackend(url: String) {
        val base = url.trim().trimEnd('/')
        if (base.isBlank()) {
            throw SearchUnavailableException("Enter a backend URL first.", null)
        }
        try {
            client.get("$base/search") {
                parameter("q", "test")
                parameter("filter", "music_songs")
            }.body<ServerSearchResponse>()
        } catch (e: Exception) {
            throw SearchUnavailableException(e.message ?: e.toString(), e)
        }
    }

    private suspend fun searchServer(
        baseUrl: String,
        query: String,
        downloadable: Boolean,
        serverBaseUrl: String = "",
        filter: String = "music_songs"
    ): List<Track> {
        val response: ServerSearchResponse = client.get("$baseUrl/search") {
            parameter("q", query)
            parameter("filter", filter)
        }.body()
        return response.items.mapNotNull { it.toTrackOrNull(downloadable, serverBaseUrl) }
    }

    private fun ServerItem.toTrackOrNull(downloadable: Boolean, serverBaseUrl: String = ""): Track? {
        val itemUrl = url ?: return null
        val name = title ?: return null
        val itemId = extractItemId(itemUrl) ?: return null
        return Track(
            id = SERVER_TRACK_ID_PREFIX + itemId,
            title = name,
            artist = uploaderName ?: "Unknown artist",
            durationLabel = formatDuration(duration),
            thumbnailUrl = matchServerScheme(thumbnail.orEmpty(), serverBaseUrl),
            source = TrackSource.ONLINE,
            itemId = itemId,

            streamUrl = "",
            downloadable = downloadable,
            serverBaseUrl = serverBaseUrl,
            modifiedAt = modified
        )
    }

    private fun extractItemId(itemUrl: String): String? {
        val marker = "v="
        val start = itemUrl.indexOf(marker)
        if (start < 0) return null
        val id = itemUrl.substring(start + marker.length).substringBefore('&')
        return id.ifBlank { null }
    }

    private fun formatDuration(seconds: Long?): String {
        if (seconds == null || seconds <= 0) return ""
        val mins = seconds / 60
        val secs = seconds % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }

    companion object {
        private const val AUDIO_PIECE_BYTES = 8L * 1024 * 1024

        const val MIN_COMPLETE_BYTES = 32L * 1024
    }
}
