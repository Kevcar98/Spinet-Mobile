package dev.spinet.extensions

import androidx.compose.runtime.Composable

class PickedExtensionFile(val name: String, val bytes: ByteArray)

@Composable
expect fun PickExtensionFileButton(text: String, onPicked: (files: List<PickedExtensionFile>) -> Unit)

@Composable
expect fun ExtensionDropTarget(
    message: String?,
    onDropped: (files: List<PickedExtensionFile>) -> Unit,
    content: @Composable () -> Unit
)

expect suspend fun installExtensionFile(fileName: String, bytes: ByteArray): InstallOutcome

@Composable
expect fun OnAppResumed(action: () -> Unit)

expect suspend fun uninstallExtension(origin: String): InstallOutcome
