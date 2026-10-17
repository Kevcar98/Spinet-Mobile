package dev.spinet.extensions

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import dev.spinet.local.PlatformAppContext
import dev.spinet.update.AndroidUpdateInstaller

actual fun scanExtensions(): ExtensionScan {
    val context = PlatformAppContext.appOrNull ?: return ExtensionScan()
    val result = ExtensionLoader.loadAll(context)
    return ExtensionScan(
        sources = result.loaded.flatMap { it.sources },
        rejected = result.rejected.map { RejectedExtension(it.packageName, it.reason) },
        origins = result.loaded.flatMap { l -> l.sources.mapNotNull { s -> runCatching { s.id }.getOrNull()?.let { it to l.packageName } } }.toMap()
    ).withoutDuplicateIds()
}

actual val extensionPlatform: String? = "android"

actual fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

actual suspend fun installExtensionArtifact(id: String, bytes: ByteArray): InstallOutcome {
    val context = PlatformAppContext.appOrNull
        ?: return InstallOutcome(false, "Couldn't start the installer.")
    needsInstallPermission(context)?.let { return it }
    val apk = File(AndroidUpdateInstaller.downloadDir(context), id.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".apk")
    apk.writeBytes(bytes)
    return handToInstaller(context, apk)
}

private fun handToInstaller(context: Context, apk: File): InstallOutcome {
    return if (AndroidUpdateInstaller.install(context, apk)) {
        InstallOutcome(true, "Confirm the install. It appears above when you're back.", InstallOutcome.Next.AFTER_INSTALLER)
    } else {
        InstallOutcome(false, "Couldn't open the installer.")
    }
}

private fun needsInstallPermission(context: Context): InstallOutcome? {
    if (AndroidUpdateInstaller.canInstall(context)) return null
    AndroidUpdateInstaller.requestInstallPermission(context)
    return InstallOutcome(false, "Allow Spinet to install apps, then come back.", InstallOutcome.Next.AFTER_PERMISSION)
}

@Composable
actual fun PickExtensionFileButton(text: String, onPicked: (files: List<PickedExtensionFile>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@mapNotNull null
                    PickedExtensionFile(displayName(context, uri), bytes)
                }
            }
            if (picked.isNotEmpty()) onPicked(picked)
        }
    }
    OutlinedButton(onClick = {
        launcher.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
    }) { Text(text) }
}

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "extension.apk"

actual suspend fun installExtensionFile(fileName: String, bytes: ByteArray): InstallOutcome {
    val context = PlatformAppContext.appOrNull
        ?: return InstallOutcome(false, "Couldn't start the installer.")
    val apk = File(AndroidUpdateInstaller.downloadDir(context), "picked-extension.apk")
    withContext(Dispatchers.IO) { apk.writeBytes(bytes) }
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_META_DATA)
    val isExtension = info != null &&
        info.packageName.startsWith("dev.spinet.extension.") &&
        info.applicationInfo?.metaData?.getString("spinet.extension.factory") != null
    if (!isExtension) {
        apk.delete()
        return InstallOutcome(false, "That file isn't a Spinet extension.")
    }
    needsInstallPermission(context)?.let { return it }
    return handToInstaller(context, apk)
}

@Composable
actual fun OnAppResumed(action: () -> Unit) {
    LifecycleResumeEffect(Unit) {
        action()
        onPauseOrDispose { }
    }
}

actual suspend fun uninstallExtension(origin: String): InstallOutcome {
    val context = PlatformAppContext.appOrNull
        ?: return InstallOutcome(false, "Couldn't open the uninstaller.")
    if (!origin.startsWith("dev.spinet.extension.")) {
        return InstallOutcome(false, "That isn't an extension package.")
    }
    return runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_DELETE, Uri.parse("package:$origin"))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        InstallOutcome(true, "Confirm removing {name}.", InstallOutcome.Next.AFTER_INSTALLER)
    }.getOrDefault(InstallOutcome(false, "Couldn't open the uninstaller."))
}

@Composable
actual fun ExtensionDropTarget(
    message: String?,
    onDropped: (files: List<PickedExtensionFile>) -> Unit,
    content: @Composable () -> Unit
) = content()
