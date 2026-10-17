package dev.spinet.data

import kotlinx.serialization.Serializable

@Serializable
data class ServerSearchResponse(
    val items: List<ServerItem> = emptyList()
)

@Serializable
data class ServerItem(
    val url: String? = null,
    val title: String? = null,
    val uploaderName: String? = null,
    val thumbnail: String? = null,
    val duration: Long? = null,

    val size: Long? = null,

    val modified: Long? = null,
    val type: String? = null
)

@Serializable
data class ServerStreamsResponse(
    val audioStreams: List<ServerAudioStream> = emptyList(),

    val title: String? = null,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Long? = null
)

@Serializable
data class ServerAudioStream(
    val url: String? = null,
    val bitrate: Int? = null,
    val mimeType: String? = null,

    val audioTrackType: String? = null
)
