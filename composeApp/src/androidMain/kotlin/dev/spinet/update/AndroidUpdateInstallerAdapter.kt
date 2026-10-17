package dev.spinet.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AndroidUpdateInstallerAdapter(private val context: Context) : UpdateInstaller {
    override fun wants(assetName: String): Boolean = AndroidUpdateInstaller.wants(assetName)

    override fun blockedReason(): String? =
        if (AndroidUpdateInstaller.canInstall(context)) null
        else "Android needs permission to install apps from this app before it can update itself."

    override fun resolveBlock() = AndroidUpdateInstaller.requestInstallPermission(context)

    override suspend fun install(
        assetName: String,
        bytes: ByteArray,
        checksums: String?
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(AndroidUpdateInstaller.downloadDir(context), assetName)
            file.writeBytes(bytes)

            if (!AndroidUpdateInstaller.verify(file, checksums, assetName)) {
                file.delete()
                return@withContext "The download did not match the published checksum, so it was discarded."
            }

            if (AndroidUpdateInstaller.install(context, file)) null
            else "Could not open the installer. The current version is untouched."
        }.getOrElse { "Could not start the update: ${it.message ?: it::class.simpleName}" }
    }
}
