package dev.spinet.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
actual fun ExportPluginFileButton(content: () -> String) {
    OutlinedButton(onClick = {}, enabled = false) {
        Text("Export to file (Android)")
    }
}
