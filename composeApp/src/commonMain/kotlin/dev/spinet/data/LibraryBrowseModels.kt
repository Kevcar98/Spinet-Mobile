package dev.spinet.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrowsePlaylistsResponse(
    val playlists: List<BrowsePlaylistDto> = emptyList()
)

@Serializable
data class BrowsePlaylistDto(
    val id: String = "",
    val name: String = "",
    val trackCount: Int = 0
)

@Serializable
data class ServerTransfersResponse(
    val transfers: List<ServerTransferDto> = emptyList()
)

@Serializable
data class ServerTransferDto(
    val at: Long = 0L,

    val event: String = "",
    val file: String = "",
    val folder: String = "",
    val title: String = "",
    val artist: String = "",
    val size: Long = 0L
)

@Serializable
data class ImportResponse(
    val saved: String? = null,

    val received: Long? = null,

    val size: Long? = null
)
