package dev.spinet.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp

private const val ICON_PREFIX = "icon:"

private val NAMED_ICONS: Map<String, ImageVector> = mapOf(
    "album" to Icons.Filled.Album,
    "church" to Icons.Filled.Church,
    "directions_car" to Icons.Filled.DirectionsCar,
    "edit" to Icons.Filled.Edit,
    "family_restroom" to Icons.Filled.FamilyRestroom,
    "language" to Icons.Filled.Language,
    "local_cafe" to Icons.Filled.LocalCafe,
    "movie" to Icons.Filled.Movie,
    "park" to Icons.Filled.Park,
    "sports_esports" to Icons.Filled.SportsEsports,
    "bedtime" to Icons.Filled.Bedtime,
    "bolt" to Icons.Filled.Bolt,
    "celebration" to Icons.Filled.Celebration,
    "center_focus_strong" to Icons.Filled.CenterFocusStrong,
    "favorite" to Icons.Filled.Favorite,
    "festival" to Icons.Filled.Festival,
    "fitness_center" to Icons.Filled.FitnessCenter,
    "folder" to Icons.Filled.Folder,
    "graphic_eq" to Icons.Filled.GraphicEq,
    "headphones" to Icons.Filled.Headphones,
    "history" to Icons.Filled.History,
    "landscape" to Icons.Filled.Landscape,
    "leaderboard" to Icons.Filled.Leaderboard,
    "library_music" to Icons.Filled.LibraryMusic,
    "mic" to Icons.Filled.Mic,
    "mood" to Icons.Filled.Mood,
    "music_note" to Icons.Filled.MusicNote,
    "new_releases" to Icons.Filled.NewReleases,
    "nightlife" to Icons.Filled.Nightlife,
    "piano" to Icons.Filled.Piano,
    "public" to Icons.Filled.Public,
    "queue_music" to Icons.AutoMirrored.Filled.QueueMusic,
    "radio" to Icons.Filled.Radio,
    "sentiment_dissatisfied" to Icons.Filled.SentimentDissatisfied,
    "sentiment_very_satisfied" to Icons.Filled.SentimentVerySatisfied,
    "spa" to Icons.Filled.Spa,
    "speaker" to Icons.Filled.Speaker,
    "stars" to Icons.Filled.Stars,
    "wb_sunny" to Icons.Filled.WbSunny,
    "whatshot" to Icons.Filled.Whatshot
)

@Composable
internal fun ItemArt(artworkUrl: String, size: Dp) {
    val named = if (artworkUrl.startsWith(ICON_PREFIX)) NAMED_ICONS[artworkUrl.removePrefix(ICON_PREFIX)] else null
    when {
        named != null || artworkUrl.isBlank() || artworkUrl.startsWith(ICON_PREFIX) ->
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                Icon(
                    named ?: Icons.Filled.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        else -> TrackThumbnail(artworkUrl, size = size)
    }
}
