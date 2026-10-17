package dev.spinet.audio

import android.net.Uri
import androidx.media3.common.MediaMetadata
import java.io.File

internal fun MediaMetadata.Builder.setArtwork(artworkUrl: String?): MediaMetadata.Builder {
    val art = artworkUrl?.trim().orEmpty()
    if (art.isBlank()) return this
    if (art.startsWith("http://", true) ||
        art.startsWith("https://", true) ||
        art.startsWith("content://", true)
    ) {
        return setArtworkUri(Uri.parse(art))
    }
    val file = runCatching {
        File(if (art.startsWith("file://", true)) Uri.parse(art).path.orEmpty() else art)
    }.getOrNull()

    if (file == null || !file.canRead()) return this
    val bytes = runCatching { file.readBytes() }.getOrNull()
    if (bytes == null || bytes.isEmpty()) return this
    return setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
}
