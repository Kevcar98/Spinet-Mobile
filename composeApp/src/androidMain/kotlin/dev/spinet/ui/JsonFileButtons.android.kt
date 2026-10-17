package dev.spinet.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
actual fun OpenCsvFileButton(label: String, onFileRead: (String) -> Unit) {
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
