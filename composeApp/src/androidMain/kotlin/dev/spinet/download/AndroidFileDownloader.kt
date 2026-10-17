package dev.spinet.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.spinet.local.LocalMetadataStore
import dev.spinet.settings.SettingsRepository
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

class AndroidFileDownloader(context: Context) : FileDownloader {
    private val appContext = context.applicationContext
    private val settings = SettingsRepository()

    override suspend fun downloadAudio(
        url: String,
        title: String,
        artist: String,
        artworkUrl: String,
        subfolder: String,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val base = if (artist.isNotBlank()) "$artist - $title" else title
        val stem = sanitize(base)
        val folder = subfolder.trim().let { if (it.isBlank()) "" else sanitize(it) }

        if (fileExists("$stem.mp3", folder) || fileExists("$stem.m4a", folder)) {
            return@withContext DownloadResult.Success(existingLocation(folder))
        }
        onProgress(-1f)
        val raw = fetchBytes(url) ?: return@withContext DownloadResult.Error("Download failed")
        if (raw.size < 32 * 1024) {
            return@withContext DownloadResult.Error("Download too small (${raw.size} bytes)")
        }
        val artwork = fetchArtwork(artworkUrl)
        onProgress(1f)

        val isMp4 = raw.size >= 8 &&
            raw[4] == 'f'.code.toByte() && raw[5] == 't'.code.toByte() &&
            raw[6] == 'y'.code.toByte() && raw[7] == 'p'.code.toByte()
        if (isMp4) {
            val tagged = Mp4Tagger.tag(raw, title, artist, artwork)
            saveViaWriter("$stem.m4a", folder, "audio/mp4", onUri = { uri ->
                LocalMetadataStore.record(appContext, uri.toString(), title, artist, artwork)
            }) { it.write(tagged) }
        } else {
            val tag = id3v2Tag(title = title, artist = artist, artwork = artwork)

            var offset = 0
            if (raw.size >= 10 &&
                raw[0] == 'I'.code.toByte() && raw[1] == 'D'.code.toByte() && raw[2] == '3'.code.toByte()
            ) {
                val existing = decodeSynchsafe(raw, 6)
                val footer = if ((raw[5].toInt() and 0x10) != 0) 10 else 0
                offset = (10 + existing + footer).coerceAtMost(raw.size)
            }
            saveViaWriter("$stem.mp3", folder, "audio/mpeg") { out ->
                if (tag.isNotEmpty()) out.write(tag)
                out.write(raw, offset, raw.size - offset)
            }
        }
    }

    override suspend fun downloadRaw(
        url: String,
        title: String,
        artist: String,
        ext: String,
        mime: String,
        subfolder: String,
        artworkUrl: String,
        onProgress: (Float) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val base = if (artist.isNotBlank()) "$artist - $title" else title
        val safeExt = ext.ifBlank { "m4a" }
        val fileName = "${sanitize(base)}.$safeExt"
        val folder = subfolder.trim().let { if (it.isBlank()) "" else sanitize(it) }
        val finalMime = mime.ifBlank { "audio/mp4" }

        if (fileExists(fileName, folder)) {
            return@withContext DownloadResult.Success(existingLocation(folder))
        }

        val canTag = (safeExt.equals("m4a", true) || finalMime.contains("mp4")) &&
            (title.isNotBlank() || artist.isNotBlank() || artworkUrl.isNotBlank())
        if (canTag) {
            val raw = fetchBytes(url) ?: return@withContext DownloadResult.Error("Download failed")

            if (raw.size < 32 * 1024) {
                return@withContext DownloadResult.Error("Download too small (${raw.size} bytes)")
            }
            val art = fetchArtwork(artworkUrl)
            val tagged = Mp4Tagger.tag(raw, title, artist, art)
            onProgress(1f)

            saveViaWriter(fileName, folder, finalMime, onUri = { uri ->
                LocalMetadataStore.record(appContext, uri.toString(), title, artist, art)
            }) { it.write(tagged) }
        } else {
            saveViaWriter(fileName, folder, finalMime) { streamInto(url, it, ByteArray(0), onProgress) }
        }
    }

    override suspend fun saveRepairedAudio(
        bytes: ByteArray,
        title: String,
        artist: String,
        subfolder: String
    ): Boolean = withContext(Dispatchers.IO) {
        val base = if (artist.isNotBlank()) "$artist - $title" else title
        val stem = sanitize(base)
        val folder = subfolder.trim().let { if (it.isBlank()) "" else sanitize(it) }
        val tagged = Mp4Tagger.tag(bytes, title, artist, null)
        val result = saveViaWriter("$stem.m4a", folder, "audio/mp4", onUri = { uri ->
            LocalMetadataStore.record(appContext, uri.toString(), title, artist, null)
        }) { it.write(tagged) }
        result is DownloadResult.Success
    }

    private fun saveViaWriter(
        fileName: String,
        folder: String,
        mime: String,
        onUri: (Uri) -> Unit = {},
        writer: (OutputStream) -> Unit
    ): DownloadResult {
        val folderUri = settings.localFolderUri
        if (folderUri.isNotBlank()) {
            val saf = saveToFolder(folderUri, fileName, folder, mime, onUri, writer)
            if (saf != null) return saf
        }
        return saveToMediaStore(fileName, folder, mime, onUri, writer)
    }

    override suspend fun tagAudio(
        bytes: ByteArray,
        title: String,
        artist: String,
        ext: String,
        artworkUrl: String
    ): ByteArray = withContext(Dispatchers.IO) {
        if (!ext.equals("m4a", true)) return@withContext bytes
        val art = fetchArtwork(artworkUrl)
        Mp4Tagger.tag(bytes, title, artist, art)
    }

    override suspend fun saveLocalArtwork(
        fileKey: String,
        title: String,
        artist: String,
        artworkUrl: String
    ): Boolean = withContext(Dispatchers.IO) {
        val art = fetchArtwork(artworkUrl)
        LocalMetadataStore.record(appContext, fileKey, title, artist, art)
        art != null
    }

    private fun fetchBytes(url: String): ByteArray? = try {
        PiecewiseHttpInputStream(url) {
            (URL(it).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }
        }.use { it.readBytes() }.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    private fun saveToFolder(
        folderUri: String,
        fileName: String,
        subfolder: String,
        mime: String,
        onUri: (Uri) -> Unit = {},
        writer: (OutputStream) -> Unit
    ): DownloadResult? {
        return try {
            val resolver = appContext.contentResolver
            val tree = Uri.parse(folderUri)
            var parentDocId = DocumentsContract.getTreeDocumentId(tree)
            if (subfolder.isNotBlank()) {
                parentDocId = findOrCreateChildDir(resolver, tree, parentDocId, subfolder)
                    ?: return null
            }
            val dirUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId)
            val fileUri = DocumentsContract.createDocument(
                resolver, dirUri, mime, fileName
            ) ?: return null

            val output = resolver.openOutputStream(fileUri)
                ?: throw IOException("Could not open output stream")
            output.use { writer(it) }
            onUri(fileUri)
            val where = settings.localFolderLabel.ifBlank { "your music folder" }
            DownloadResult.Success(if (subfolder.isBlank()) where else "$where/$subfolder")
        } catch (e: Exception) {
            null
        }
    }

    private fun existingLocation(subfolder: String): String {
        val where = settings.localFolderLabel.ifBlank { "your music folder" }
        return if (subfolder.isBlank()) where else "$where/$subfolder"
    }

    private fun fileExists(fileName: String, folder: String): Boolean {
        val folderUri = settings.localFolderUri
        return if (folderUri.isNotBlank()) safFileExists(folderUri, folder, fileName)
        else mediaStoreFileExists(fileName, folder)
    }

    private fun safFileExists(folderUri: String, subfolder: String, fileName: String): Boolean =
        try {
            val resolver = appContext.contentResolver
            val tree = Uri.parse(folderUri)
            var parentDocId = DocumentsContract.getTreeDocumentId(tree)
            val ok = if (subfolder.isBlank()) true else {
                val dir = findChildDir(resolver, tree, parentDocId, subfolder)
                if (dir != null) { parentDocId = dir; true } else false
            }
            if (!ok) false else {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
                var found = false
                resolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(0) == fileName) { found = true; break }
                    }
                }
                found
            }
        } catch (e: Exception) {
            false
        }

    private fun mediaStoreFileExists(fileName: String, subfolder: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = appContext.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath =
            if (subfolder.isBlank()) "${Environment.DIRECTORY_MUSIC}/"
            else "${Environment.DIRECTORY_MUSIC}/$subfolder/"
        return try {
            resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH}=?",
                arrayOf(fileName, relativePath),
                null
            )?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun findChildDir(
        resolver: android.content.ContentResolver,
        tree: Uri,
        parentDocId: String,
        name: String
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR &&
                        c.getString(1) == name
                    ) {
                        return c.getString(0)
                    }
                }
            }
        }
        return null
    }

    private fun findOrCreateChildDir(
        resolver: android.content.ContentResolver,
        tree: Uri,
        parentDocId: String,
        name: String
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR &&
                        c.getString(1) == name
                    ) {
                        return c.getString(0)
                    }
                }
            }
        }
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId)
        val created = runCatching {
            DocumentsContract.createDocument(
                resolver, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, name
            )
        }.getOrNull() ?: return null
        return DocumentsContract.getDocumentId(created)
    }

    private fun saveToMediaStore(
        fileName: String,
        subfolder: String,
        mime: String,
        onUri: (Uri) -> Unit = {},
        writer: (OutputStream) -> Unit
    ): DownloadResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return DownloadResult.Error("Saving requires Android 10 (API 29)+")
        }
        val resolver = appContext.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath =
            if (subfolder.isBlank()) Environment.DIRECTORY_MUSIC
            else "${Environment.DIRECTORY_MUSIC}/$subfolder"

        val pendingValues = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }

        val itemUri = resolver.insert(collection, pendingValues)
            ?: return DownloadResult.Error("Could not create MediaStore entry")

        return try {
            val output = resolver.openOutputStream(itemUri)
                ?: throw IOException("Could not open output stream")
            output.use { writer(it) }
            resolver.update(
                itemUri,
                ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) },
                null, null
            )
            onUri(itemUri)
            DownloadResult.Success("$relativePath/$fileName")
        } catch (e: Exception) {
            resolver.delete(itemUri, null, null)
            DownloadResult.Error(e.message ?: "Download failed")
        }
    }

    private fun streamInto(
        url: String,
        output: OutputStream,
        tag: ByteArray,
        onProgress: (Float) -> Unit
    ) {
        val stream = PiecewiseHttpInputStream(url) {
            (URL(it).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000

                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
            }
        }
        val total = stream.totalLength
        stream.use { input ->
            onProgress(if (total > 0) 0f else -1f)

            val header = ByteArray(10)
            val headerRead = readFully(input, header)
            var done = headerRead.toLong()
            if (tag.isNotEmpty()) output.write(tag)

            if (tag.isNotEmpty() && headerRead == 10 &&
                header[0] == 'I'.code.toByte() &&
                header[1] == 'D'.code.toByte() &&
                header[2] == '3'.code.toByte()
            ) {
                val existingSize = decodeSynchsafe(header, 6)
                val hasFooter = (header[5].toInt() and 0x10) != 0
                var toSkip = existingSize.toLong() + if (hasFooter) 10L else 0L
                val skipBuf = ByteArray(64 * 1024)
                while (toSkip > 0) {
                    val n = input.read(skipBuf, 0, minOf(skipBuf.size.toLong(), toSkip).toInt())
                    if (n < 0) break
                    toSkip -= n
                    done += n
                }
            } else if (headerRead > 0) {
                output.write(header, 0, headerRead)
            }

            val buffer = ByteArray(64 * 1024)
            var read = input.read(buffer)
            while (read >= 0) {
                if (TransferController.cancelled) throw IOException("Cancelled")
                output.write(buffer, 0, read)
                done += read
                if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                read = input.read(buffer)
            }

            if (total > 0 && done < total) {
                throw IOException("Incomplete download ($done/$total bytes)")
            }
            onProgress(1f)
        }
    }

    private fun readFully(input: java.io.InputStream, dst: ByteArray): Int {
        var off = 0
        while (off < dst.size) {
            val n = input.read(dst, off, dst.size - off)
            if (n < 0) break
            off += n
        }
        return off
    }

    private fun decodeSynchsafe(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0x7F) shl 21) or
            ((b[offset + 1].toInt() and 0x7F) shl 14) or
            ((b[offset + 2].toInt() and 0x7F) shl 7) or
            (b[offset + 3].toInt() and 0x7F)

    private fun sanitize(title: String): String =
        title.replace(Regex("[^A-Za-z0-9-_ ]"), "_").trim().ifEmpty { "track" }

    private fun fetchArtwork(artworkUrl: String): ByteArray? {
        if (artworkUrl.isBlank() || !artworkUrl.startsWith("http")) return null
        return try {
            val connection = (URL(artworkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            connection.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private fun id3v2Tag(title: String, artist: String, artwork: ByteArray?): ByteArray {
        val frames = ArrayList<Byte>()
        appendTextFrame(frames, "TIT2", title)
        appendTextFrame(frames, "TPE1", artist)
        if (artwork != null) appendApicFrame(frames, artwork)
        if (frames.isEmpty()) return ByteArray(0)

        val header = ArrayList<Byte>()
        header.addAll("ID3".toByteArray(Charsets.US_ASCII).toList())
        header.add(0x04); header.add(0x00)
        header.add(0x00)
        header.addAll(synchsafe(frames.size).toList())
        return header.toByteArray() + frames.toByteArray()
    }

    private fun appendTextFrame(out: ArrayList<Byte>, id: String, text: String) {
        if (text.isBlank()) return

        val body = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + body.size + 1)
        payload[0] = 0x03
        System.arraycopy(body, 0, payload, 1, body.size)
        payload[payload.size - 1] = 0x00

        out.addAll(id.toByteArray(Charsets.US_ASCII).toList())
        out.addAll(synchsafe(payload.size).toList())
        out.add(0x00); out.add(0x00)
        out.addAll(payload.toList())
    }

    private fun appendApicFrame(out: ArrayList<Byte>, image: ByteArray) {
        val mime = when {
            image.size >= 8 && image[0].toInt() and 0xFF == 0x89 &&
                image[1].toInt() and 0xFF == 0x50 -> "image/png"
            else -> "image/jpeg"
        }
        val payload = ArrayList<Byte>()
        payload.add(0x03)
        payload.addAll(mime.toByteArray(Charsets.US_ASCII).toList())
        payload.add(0x00)
        payload.add(0x03)
        payload.add(0x00)
        payload.addAll(image.toList())

        out.addAll("APIC".toByteArray(Charsets.US_ASCII).toList())
        out.addAll(synchsafe(payload.size).toList())
        out.add(0x00); out.add(0x00)
        out.addAll(payload)
    }

    private fun synchsafe(v: Int): ByteArray = byteArrayOf(
        ((v ushr 21) and 0x7F).toByte(),
        ((v ushr 14) and 0x7F).toByte(),
        ((v ushr 7) and 0x7F).toByte(),
        (v and 0x7F).toByte()
    )
}
