package dev.spinet.local

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

object LocalMetadataStore {
    @Serializable
    data class Meta(val title: String, val artist: String, val artPath: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    private fun storeFile(ctx: Context) = File(ctx.filesDir, "local_meta.json")
    private fun artDir(ctx: Context) = File(ctx.filesDir, "local_meta_art").apply { mkdirs() }

    private fun load(ctx: Context): MutableMap<String, Meta> = try {
        val f = storeFile(ctx)
        if (!f.exists()) mutableMapOf()
        else json.decodeFromString<Map<String, Meta>>(f.readText()).toMutableMap()
    } catch (_: Exception) {
        mutableMapOf()
    }

    private fun persist(ctx: Context, map: Map<String, Meta>) {
        runCatching { storeFile(ctx).writeText(json.encodeToString(map)) }
    }

    fun record(ctx: Context, key: String, title: String, artist: String, artBytes: ByteArray?) {
        synchronized(lock) {
            val map = load(ctx)
            val artPath = if (artBytes != null && artBytes.isNotEmpty()) {
                runCatching {
                    val f = File(artDir(ctx), "${key.hashCode()}.img")
                    f.writeBytes(artBytes)
                    f.absolutePath
                }.getOrDefault(map[key]?.artPath ?: "")
            } else {
                map[key]?.artPath ?: ""
            }
            map[key] = Meta(title, artist, artPath)
            persist(ctx, map)
        }
    }

    fun lookup(ctx: Context, key: String): Meta? = synchronized(lock) { load(ctx)[key] }

    fun artUri(ctx: Context, key: String): String {
        val path = lookup(ctx, key)?.artPath.orEmpty()
        if (path.isBlank() || !File(path).exists()) return ""
        return Uri.fromFile(File(path)).toString()
    }
}
