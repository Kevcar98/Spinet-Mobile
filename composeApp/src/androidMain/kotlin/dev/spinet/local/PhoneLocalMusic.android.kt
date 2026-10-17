package dev.spinet.local

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.spinet.model.Track
import dev.spinet.model.TrackSource

@Composable
actual fun PickLocalFolderButton(text: String, onPicked: (LocalFolder) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val label = folderLabel(uri)
            onPicked(LocalFolder(uri.toString(), label))
        }
    }
    OutlinedButton(onClick = { launcher.launch(null) }) {
        Text(text)
    }
}

private fun folderLabel(tree: Uri): String {
    val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
        ?: return "Selected folder"
    val name = id.substringAfterLast(':').substringAfterLast('/')
    return name.ifBlank { "Selected folder" }
}

private val AUDIO_EXTS = setOf(
    "mp3", "flac", "m4a", "m4b", "mp4", "ogg", "oga", "opus", "wav", "aac", "aiff", "aif"
)

actual suspend fun listLocalTracks(folderUri: String): List<Track> = withContext(Dispatchers.IO) {
    val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return@withContext emptyList()
    val tree = runCatching { Uri.parse(folderUri) }.getOrNull() ?: return@withContext emptyList()
    val resolver = ctx.contentResolver

    val out = ArrayList<Track>()

    val stack = ArrayDeque<Pair<String, String>>()
    stack.add(DocumentsContract.getTreeDocumentId(tree) to "")

    while (stack.isNotEmpty()) {
        val (parentId, topFolder) = stack.removeLast()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val cursor = runCatching {
            resolver.query(childrenUri, projection, null, null, null)
        }.getOrNull() ?: continue

        cursor.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childTop = topFolder.ifBlank { name }
                    stack.add(docId to childTop)
                    continue
                }
                val isAudio = mime.startsWith("audio/") ||
                    name.substringAfterLast('.', "").lowercase() in AUDIO_EXTS
                if (!isAudio) continue

                val size = if (c.isNull(3)) -1L else c.getLong(3)
                if (size == 0L) continue
                val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                out.add(buildTrack(ctx, docUri, name, topFolder))
            }
        }
    }
    out.sortedBy { it.title.lowercase() }
}

private fun buildTrack(
    ctx: android.content.Context,
    docUri: Uri,
    displayName: String,
    folder: String = ""
): Track {
    val stem = displayName.substringBeforeLast('.')
    var title = stem
    var artist = "Unknown artist"
    var durationLabel = ""
    var artUri = ""

    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(ctx, docUri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            ?.takeIf { it.isNotBlank() }?.let { title = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            ?.takeIf { it.isNotBlank() }?.let { artist = it }
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()?.let { durationLabel = formatDuration(it) }

        retriever.embeddedPicture?.let { pic -> artUri = cacheArtwork(ctx, docUri.toString(), pic) }
    } catch (_: Exception) {
    } finally {
        runCatching { retriever.release() }
    }

    if (artist == "Unknown artist") {
        val dash = stem.indexOf(" - ")
        if (dash > 0) {
            artist = stem.substring(0, dash).trim()
            title = stem.substring(dash + 3).trim()
        }
    }

    LocalMetadataStore.lookup(ctx, docUri.toString())?.let { meta ->
        if (meta.title.isNotBlank()) title = meta.title
        if (meta.artist.isNotBlank()) artist = meta.artist
        val art = LocalMetadataStore.artUri(ctx, docUri.toString())
        if (art.isNotBlank()) artUri = art
    }

    return Track(
        id = "local:${docUri}",
        title = title,
        artist = artist,
        durationLabel = durationLabel,
        thumbnailUrl = artUri,
        source = TrackSource.LOCAL,
        itemId = "",
        streamUrl = docUri.toString(),
        downloadable = false,
        localFolder = folder
    )
}

actual suspend fun readLocalTrackBytes(streamUrl: String): ByteArray? = withContext(Dispatchers.IO) {
    val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return@withContext null
    val uri = runCatching { Uri.parse(streamUrl) }.getOrNull() ?: return@withContext null
    runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull()
}

actual suspend fun writeLocalTrackBytes(streamUrl: String, bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return@withContext false
        val uri = runCatching { Uri.parse(streamUrl) }.getOrNull() ?: return@withContext false
        runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

private fun cacheArtwork(ctx: android.content.Context, docUri: String, bytes: ByteArray): String {
    return try {
        val dir = java.io.File(ctx.cacheDir, "localart").apply { mkdirs() }
        val file = java.io.File(dir, "${docUri.hashCode()}.img")
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            file.writeBytes(bytes)
        }
        Uri.fromFile(file).toString()
    } catch (_: Exception) {
        ""
    }
}

actual suspend fun deleteLocalTrack(streamUrl: String): Boolean = withContext(Dispatchers.IO) {
    val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return@withContext false
    val uri = runCatching { Uri.parse(streamUrl) }.getOrNull() ?: return@withContext false
    runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }.getOrDefault(false)
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

actual fun defaultLocalFolder(): LocalFolder? = null

actual fun isUsableLocalArt(path: String): Boolean =
    path.startsWith("content://") ||
        runCatching { java.io.File(path).isFile }.getOrDefault(false)
