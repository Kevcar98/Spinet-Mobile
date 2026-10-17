package dev.spinet.local

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.spinet.model.Track

@Composable
actual fun PickLocalFolderButton(text: String, onPicked: (LocalFolder) -> Unit) {
    OutlinedButton(onClick = {}, enabled = false) {
        Text(text)
    }
}

actual suspend fun listLocalTracks(folderUri: String): List<Track> = emptyList()

actual suspend fun readLocalTrackBytes(streamUrl: String): ByteArray? = null

actual suspend fun writeLocalTrackBytes(streamUrl: String, bytes: ByteArray): Boolean = false

actual suspend fun deleteLocalTrack(streamUrl: String): Boolean = false

actual fun defaultLocalFolder(): LocalFolder? = null

actual fun isUsableLocalArt(path: String): Boolean = false
