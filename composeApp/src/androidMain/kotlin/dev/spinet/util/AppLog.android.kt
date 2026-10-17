package dev.spinet.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import dev.spinet.APP_VERSION
import dev.spinet.local.PlatformAppContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.Date

actual val logButtonLabel: String? = "Share log"

actual fun openAppLog() {
    val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return
    runCatching {
        System.out.flush()
        val file = AppLog.file(ctx)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Spinet log")
            clipData = ClipData.newRawUri("spinet.log", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share log").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(chooser)
    }
}

actual val saveLogLabel: String? = "Save log to Downloads"

actual fun saveAppLog(): String? {
    val ctx = runCatching { PlatformAppContext.app }.getOrNull() ?: return "Couldn't save the log."
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return "Saving needs Android 10 or newer. Use Share log and pick Files instead."
    }
    return try {
        System.out.flush()
        val log = AppLog.file(ctx)
        if (!log.exists()) return "There's no log yet."
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(Date())
        val name = "spinet-log_$stamp.txt"
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = ctx.contentResolver
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return "Couldn't save the log."
        resolver.openOutputStream(uri)?.use { out -> log.inputStream().use { it.copyTo(out) } }
            ?: return "Couldn't save the log."
        "Saved to Downloads as $name"
    } catch (e: Exception) {
        "Couldn't save the log."
    }
}

internal object AppLog {

    private const val MAX_BYTES = 1_000_000L

    @Volatile private var started = false

    fun file(ctx: Context): File = File(File(ctx.cacheDir, "logs").apply { mkdirs() }, "spinet.log")

    @Synchronized
    fun start(ctx: Context) {
        if (started) return
        started = true
        runCatching {
            val log = file(ctx)
            val sink = FileOutputStream(log, log.length() < MAX_BYTES)
            System.setOut(PrintStream(Tee(System.out, sink), true, "UTF-8"))
            System.setErr(PrintStream(Tee(System.err, sink), true, "UTF-8"))
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching {
                    System.err.println("[Spinet] crashed on ${thread.name}")
                    error.printStackTrace()
                    System.err.flush()
                }
                previous?.uncaughtException(thread, error)
            }
            println("[Spinet] $APP_VERSION started ${Date()} on Android ${Build.VERSION.RELEASE} (${Build.MANUFACTURER} ${Build.MODEL})")
        }
    }

    private class Tee(private val screen: OutputStream, private val file: OutputStream) : OutputStream() {
        private val line = java.io.ByteArrayOutputStream()

        override fun write(b: Int) {
            screen.write(b)
            synchronized(line) {
                line.write(b)
                if (b == 10) writeLine()
            }
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            screen.write(b, off, len)
            synchronized(line) {
                for (i in off until off + len) {
                    line.write(b[i].toInt())
                    if (b[i] == 10.toByte()) writeLine()
                }
            }
        }

        override fun flush() {
            screen.flush()
            runCatching { file.flush() }
        }

        private fun writeLine() {
            runCatching { file.write(dev.spinet.util.LogRedactor.redact(line.toString(Charsets.UTF_8.name())).toByteArray(Charsets.UTF_8)) }
            line.reset()
        }
    }
}
