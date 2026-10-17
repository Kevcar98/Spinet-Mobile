package dev.spinet.extensions

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

actual fun scanExtensions(): ExtensionScan = ExtensionScan()

actual val extensionPlatform: String? = null

actual fun sha256Hex(bytes: ByteArray): String =
    throw UnsupportedOperationException("Extensions cannot be installed on iOS")

actual suspend fun installExtensionArtifact(id: String, bytes: ByteArray): InstallOutcome =
    InstallOutcome(false, "Extensions cannot be installed on iOS.")

@Composable
actual fun PickExtensionFileButton(text: String, onPicked: (files: List<PickedExtensionFile>) -> Unit) {
    OutlinedButton(onClick = {}, enabled = false) { Text(text) }
}

actual suspend fun installExtensionFile(fileName: String, bytes: ByteArray): InstallOutcome =
    InstallOutcome(false, "Extensions cannot be installed on iOS.")

@Composable
actual fun OnAppResumed(action: () -> Unit) = Unit

actual suspend fun uninstallExtension(origin: String): InstallOutcome =
    InstallOutcome(false, "Extensions cannot be installed on iOS.")

@Composable
actual fun ExtensionDropTarget(
    message: String?,
    onDropped: (files: List<PickedExtensionFile>) -> Unit,
    content: @Composable () -> Unit
) = content()
