package dev.spinet.library

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.spinet.model.Playlist
import dev.spinet.model.Track
import dev.spinet.model.matchKey
import dev.spinet.model.withCurrentId
import dev.spinet.settings.SettingsRepository

class LibraryRepository(
    private val settings: SettingsRepository,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private var playlists: MutableList<Playlist> = loadPlaylists().toMutableList()
    private var favorites: MutableList<Track> = loadFavorites().toMutableList()

    init {
        val deduped = favorites.distinctBy { it.matchKey() }
        if (deduped.size != favorites.size) {
            favorites = deduped.toMutableList()
            settings.favoritesJson = json.encodeToString(favorites.toList())
        }
    }

    fun playlists(): List<Playlist> = playlists.toList()
    fun favorites(): List<Track> = favorites.toList()

    fun isFavorite(trackId: String): Boolean = favorites.any { it.id == trackId }

    fun toggleFavorite(track: Track) {
        if (favorites.any { it.id == track.id }) {
            favorites = favorites.filterNot { it.id == track.id }.toMutableList()
        } else {
            favorites = (favorites.filterNot { it.matchKey() == track.matchKey() } + track)
                .toMutableList()
        }
        settings.favoritesJson = json.encodeToString(favorites.toList())
    }

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in favorites.indices || toIndex !in favorites.indices || fromIndex == toIndex) return
        val list = favorites.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        favorites = list
        settings.favoritesJson = json.encodeToString(favorites.toList())
    }

    fun createPlaylist(name: String): Playlist? {
        val clean = name.trim()
        if (clean.isBlank()) return null
        val playlist = Playlist(id = "pl_${clean.hashCode()}_${playlists.size}", name = clean)
        playlists = (playlists + playlist).toMutableList()
        persistPlaylists()
        return playlist
    }

    fun deletePlaylist(id: String) {
        playlists = playlists.filterNot { it.id == id }.toMutableList()
        persistPlaylists()
    }

    fun movePlaylist(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in playlists.indices || toIndex !in playlists.indices || fromIndex == toIndex) return
        val list = playlists.toMutableList()
        list.add(toIndex, list.removeAt(fromIndex))
        playlists = list
        persistPlaylists()
    }

    fun renamePlaylist(id: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        playlists = playlists.map { if (it.id == id) it.copy(name = clean) else it }.toMutableList()
        persistPlaylists()
    }

    fun movePlaylistTrack(playlistId: String, fromIndex: Int, toIndex: Int) {
        playlists = playlists.map { pl ->
            if (pl.id != playlistId) return@map pl
            val list = pl.tracks.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@map pl
            }
            val moved = list.removeAt(fromIndex)
            list.add(toIndex, moved)
            pl.copy(tracks = list)
        }.toMutableList()
        persistPlaylists()
    }

    fun addToPlaylist(playlistId: String, track: Track) {
        val key = track.matchKey()
        playlists = playlists.map { pl ->
            if (pl.id == playlistId &&
                pl.tracks.none { it.id == track.id || it.matchKey() == key }
            ) {
                pl.copy(tracks = pl.tracks + track)
            } else {
                pl
            }
        }.toMutableList()
        persistPlaylists()
    }

    fun addTracksToPlaylist(playlistId: String, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playlists = playlists.map { pl ->
            if (pl.id != playlistId) return@map pl
            val seen = HashSet<String>()
            pl.tracks.forEach { seen.add(it.id) }
            val prepared = tracks.map { t ->
                var id = t.id
                var n = 1

                while (!seen.add(id)) id = "${t.id}_${n++}"
                if (id == t.id) t else t.copy(id = id)
            }
            pl.copy(tracks = pl.tracks + prepared)
        }.toMutableList()
        persistPlaylists()
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) {
        playlists = playlists.map { pl ->
            if (pl.id == playlistId) pl.copy(tracks = pl.tracks.filterNot { it.id == trackId }) else pl
        }.toMutableList()
        persistPlaylists()
    }

    fun replaceInFavorites(oldId: String, newTrack: Track) {
        if (favorites.none { it.id == oldId }) return
        favorites = favorites.map { if (it.id == oldId) newTrack else it }.toMutableList()
        settings.favoritesJson = json.encodeToString(favorites.toList())
    }

    fun replaceInPlaylists(oldId: String, newTrack: Track) {
        playlists = playlists.map { pl ->
            if (pl.tracks.any { it.id == oldId }) {
                pl.copy(tracks = pl.tracks.map { if (it.id == oldId) newTrack else it })
            } else pl
        }.toMutableList()
        persistPlaylists()
    }

    fun exportPlaylistJson(id: String): String? {
        val playlist = playlists.firstOrNull { it.id == id } ?: return null
        return json.encodeToString(playlist)
    }

    fun importPlaylistJson(text: String): Playlist? {
        val parsed = try {
            json.decodeFromString<Playlist>(text)
        } catch (_: Exception) {
            return null
        }
        val fresh = parsed.copy(id = "pl_${parsed.name.hashCode()}_${playlists.size}_${text.length}")
        playlists = (playlists + fresh).toMutableList()
        persistPlaylists()
        return fresh
    }

    fun mergeSynced(syncedPlaylists: List<Playlist>, remoteFavorites: List<Track>) {
        val remotePlaylists = syncedPlaylists.map { it.copy(tracks = dedupeTracks(it.tracks)) }

        val remoteDeduped = dedupeByName(remotePlaylists)
        val remoteIds = remoteDeduped.map { it.id }.toSet()
        val remoteNames = remoteDeduped.map { normName(it.name) }.toSet()
        val localDeduped = dedupeByName(playlists)
        val localByName = localDeduped.associateBy { normName(it.name) }
        val merged = remoteDeduped.map { remote ->
            val local = localByName[normName(remote.name)]
            if (local == null) remote else unionTracks(remote, local)
        }
        val localOnly = localDeduped.filterNot { it.id in remoteIds || normName(it.name) in remoteNames }
        playlists = (merged + localOnly).toMutableList()
        persistPlaylists()

        val remoteKeys = remoteFavorites.map { it.matchKey() }.toHashSet()
        val remoteFavoriteIds = remoteFavorites.mapNotNull { it.id.takeIf(String::isNotBlank) }.toHashSet()
        favorites = dedupeTracks(
            remoteFavorites + favorites.filterNot {
                it.matchKey() in remoteKeys || it.id in remoteFavoriteIds
            }
        ).toMutableList()
        settings.favoritesJson = json.encodeToString(favorites.toList())
    }

    private fun dedupeTracks(saved: List<Track>): List<Track> {
        val seenIds = HashSet<String>()
        val seenKeys = HashSet<String>()
        return saved.map { it.withCurrentId() }.filter { t ->
            val freshId = t.id.isBlank() || seenIds.add(t.id)
            val freshKey = seenKeys.add(t.matchKey())
            freshId && freshKey
        }
    }

    private fun normName(name: String) = name.trim().lowercase()

    private fun dedupeByName(lists: List<Playlist>): List<Playlist> {
        val out = LinkedHashMap<String, Playlist>()
        for (p in lists) {
            val key = normName(p.name)
            val existing = out[key]
            out[key] = if (existing == null) p else unionTracks(existing, p)
        }
        return out.values.toList()
    }

    private fun unionTracks(primary: Playlist, other: Playlist): Playlist {
        val have = primary.tracks.map { it.matchKey() }.toHashSet()
        val haveIds = primary.tracks.mapNotNull { it.id.takeIf(String::isNotBlank) }.toHashSet()
        return primary.copy(
            tracks = dedupeTracks(
                primary.tracks + other.tracks.filterNot {
                    it.matchKey() in have || it.id in haveIds
                }
            )
        )
    }

    private fun persistPlaylists() {
        settings.playlistsJson = json.encodeToString(playlists.toList())
    }

    private fun loadPlaylists(): List<Playlist> =
        decodeOrEmpty<Playlist>(settings.playlistsJson).map { it.copy(tracks = dedupeTracks(it.tracks)) }

    private fun loadFavorites(): List<Track> = dedupeTracks(decodeOrEmpty(settings.favoritesJson))

    private inline fun <reified T> decodeOrEmpty(stored: String): List<T> =
        if (stored.isBlank()) emptyList()
        else try {
            json.decodeFromString(stored)
        } catch (_: Exception) {
            emptyList()
        }
}
