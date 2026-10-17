package dev.spinet.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = TrackSourceSerializer::class)
enum class TrackSource {
    ONLINE,
    LOCAL
}

object TrackSourceSerializer : KSerializer<TrackSource> {
    override val descriptor = PrimitiveSerialDescriptor("dev.spinet.model.TrackSource", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: TrackSource) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): TrackSource =
        if (decoder.decodeString() == TrackSource.LOCAL.name) TrackSource.LOCAL else TrackSource.ONLINE
}

@Serializable(with = SourceKindSerializer::class)
enum class SourceKind {
    SERVER,

    EXTENSION
}

object SourceKindSerializer : KSerializer<SourceKind> {
    override val descriptor = PrimitiveSerialDescriptor("dev.spinet.model.SourceKind", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: SourceKind) = encoder.encodeString(value.name)
    override fun deserialize(decoder: Decoder): SourceKind {
        val name = decoder.decodeString()
        return when {
            name == SourceKind.EXTENSION.name -> SourceKind.EXTENSION
            name == SourceKind.SERVER.name || name.endsWith("_CUSTOM") -> SourceKind.SERVER
            else -> throw SerializationException("Unknown source kind $name")
        }
    }
}

const val SERVER_TRACK_ID_PREFIX = "server_"

fun Track.withCurrentId(): Track =
    if (serverBaseUrl.isNotBlank() && itemId.isNotBlank() && id != SERVER_TRACK_ID_PREFIX + itemId) {
        copy(id = SERVER_TRACK_ID_PREFIX + itemId)
    } else {
        this
    }

@Serializable
data class SourcePlugin(
    val id: String,
    val name: String,
    val kind: SourceKind,
    val baseUrl: String = "",
    val enabled: Boolean = true,
    val builtIn: Boolean = false
)

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationLabel: String,
    val thumbnailUrl: String,
    val source: TrackSource,

    @SerialName("videoId") val itemId: String,

    val streamUrl: String,

    val downloadable: Boolean = false,

    val serverBaseUrl: String = "",

    val modifiedAt: Long? = null,

    val localFolder: String = "",

    val sourceId: String = ""
)

fun Track.matchKey(): String {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    val primary = artist
        .split(",", "&", ";", " x ", " X ", " feat", " Feat", " FEAT", " ft.", " Ft.", " ft ")
        .first()
    val a = norm(primary).takeIf { it.isNotBlank() && it != "unknownartist" } ?: ""

    val cleanTitle = title
        .replace(Regex("(?i)\\s*[(\\[]\\s*(feat\\.?|ft\\.?|with)\\b[^)\\]]*[)\\]]?"), "")
        .replace(Regex("(?i)[\\s_](feat\\.?|ft\\.?)[\\s_.].*$"), "")
    return "$a|${norm(cleanTitle)}"
}

fun Track.looseKey(): String? {
    fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val cleanTitle = title

        .replace(Regex("[(\\[][^)\\]]*[)\\]]?"), "")
        .replace(Regex("(?i)[\\s_](feat\\.?|ft\\.?)[\\s_.].*$"), "")

        .replace(Regex("\\s*\\+\\s.*$"), "")
    val t = norm(cleanTitle)
    return if (t.length < 4) null else t
}

@Serializable
data class NowPlayingState(
    val track: Track,
    val positionMs: Long = 0,
    val durationMs: Long = 0,

    val updatedAt: Long = 0,
    val device: String = "",

    val isPlaying: Boolean = false,

    val history: List<Track> = emptyList(),

    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val sourceQueue: List<Track> = emptyList(),
    val shuffle: Boolean = false,

    val repeat: String = "OFF"
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val tracks: List<Track> = emptyList()
)

@Serializable
data class PlaylistSyncState(
    val playlists: List<Playlist> = emptyList(),
    val favorites: List<Track> = emptyList(),

    val updatedAt: Long = 0,
    val device: String = "",

    val playStatsJson: String = "",

    val themes: List<ThemeSpec> = emptyList(),

    val sources: List<SourcePlugin> = emptyList(),

    val selectedThemeId: String = "",

    val settingsOrderCsv: String = ""
)

@Serializable
data class ThemeSpec(
    val id: String,
    val name: String,
    val primary: String,
    val secondary: String,
    val tertiary: String,
    val background: String,
    val dark: Boolean = false,
    val builtIn: Boolean = false
)

