package dev.spinet.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun ImportPluginFileButton(onFileRead: (String) -> Unit) {
    OutlinedButton(onClick = {}, enabled = false) {
        Text("Import from file (Android)")
    }
}
