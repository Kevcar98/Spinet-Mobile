package dev.spinet.data

import dev.spinet.model.Track

data class ExploreSource(val pluginId: String, val name: String)

data class ExploreFolder(val id: String, val name: String, val artworkUrl: String = "")

data class ExplorePage(
    val folders: List<ExploreFolder> = emptyList(),
    val tracks: List<Track> = emptyList()
)

data class ExploreShelf(val title: String, val tracks: List<Track>)

data class PlaylistHit(val source: ExploreSource, val folder: ExploreFolder)
