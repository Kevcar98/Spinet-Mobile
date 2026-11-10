package dev.spinet.ui

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.util.UUID

@Composable
actual fun SaveJsonFileButton(label: String, suggestedName: String, content: () -> String) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(content())
            }
        }
    }
    OutlinedButton(onClick = { launcher.launch(suggestedName) }) {
        Text(label)
    }
}

@Composable
actual fun OpenCsvFileButton(label: String, onFileRead: (name: String, text: String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            val name = runCatching {
                context.contentResolver
                    .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getString(0) else null }
            }.getOrNull().orEmpty().substringBeforeLast('.')
            if (!text.isNullOrBlank()) onFileRead(name, text)
        }
    }

    OutlinedButton(onClick = { launcher.launch("*/*") }) {
        Text(label)
    }
}

@Composable
actual fun rememberJsonSaver(): (String, String) -> Unit {
    val context = LocalContext.current

    val pending = remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val text = pending.value
        pending.value = null
        if (uri != null && text != null) {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                it.write(text)
            }
        }
    }
    return { suggestedName, content ->
        pending.value = content
        launcher.launch(suggestedName)
    }
}

@Composable
actual fun rememberJsonOpener(onFileRead: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val text = context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
            if (!text.isNullOrBlank()) onFileRead(text)
        }
    }
    return { launcher.launch("application/json") }
}

@Composable
actual fun rememberImagePicker(onPicked: (path: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val ext = when (context.contentResolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }
            val dir = File(context.filesDir, "playlist-art").apply { mkdirs() }
            val target = File(dir, "${UUID.randomUUID()}.$ext")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }.getOrNull() != null
            if (copied && target.length() > 0) onPicked(target.absolutePath)
        }
    }
    return { launcher.launch("image/*") }
}
