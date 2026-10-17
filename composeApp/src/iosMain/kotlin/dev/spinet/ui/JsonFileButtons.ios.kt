package dev.spinet.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun SaveJsonFileButton(label: String, suggestedName: String, content: () -> String) {
    OutlinedButton(onClick = {}, enabled = false) { Text(label) }
}

@Composable
actual fun OpenCsvFileButton(label: String, onFileRead: (String) -> Unit) {
    OutlinedButton(onClick = {}, enabled = false) { Text(label) }
}

@Composable
actual fun rememberJsonSaver(): (String, String) -> Unit = { _, _ -> }

@Composable
actual fun rememberJsonOpener(onFileRead: (String) -> Unit): () -> Unit = {}
