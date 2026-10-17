package dev.spinet.ui

import androidx.compose.runtime.Composable

@Composable
expect fun ImportPluginFileButton(onFileRead: (String) -> Unit)
