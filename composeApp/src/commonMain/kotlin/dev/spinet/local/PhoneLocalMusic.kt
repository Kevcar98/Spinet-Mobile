package dev.spinet.local

import androidx.compose.runtime.Composable
import dev.spinet.model.Track

data class LocalFolder(val uri: String, val label: String)

@Composable
expect fun PickLocalFolderButton(text: String, onPicked: (LocalFolder) -> Unit)

expect suspend fun listLocalTracks(folderUri: String): List<Track>

expect fun defaultLocalFolder(): LocalFolder?

expect fun isUsableLocalArt(path: String): Boolean

fun isUsableArt(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true) ||
        (url.isNotBlank() && isUsableLocalArt(url))

fun portableArt(url: String): String =
    if (url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
    ) url else ""

expect suspend fun readLocalTrackBytes(streamUrl: String): ByteArray?

expect suspend fun writeLocalTrackBytes(streamUrl: String, bytes: ByteArray): Boolean

expect suspend fun deleteLocalTrack(streamUrl: String): Boolean
