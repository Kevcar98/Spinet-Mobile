package dev.spinet.data

data class ExternalTrackRef(val title: String, val artist: String)

data class PlaylistImportResult(
    val sourceName: String,
    val matched: List<dev.spinet.model.Track>,
    val unmatched: List<ExternalTrackRef> = emptyList()
) {
    val unmatchedCount: Int get() = unmatched.size
    val totalCount: Int get() = matched.size + unmatched.size
}
