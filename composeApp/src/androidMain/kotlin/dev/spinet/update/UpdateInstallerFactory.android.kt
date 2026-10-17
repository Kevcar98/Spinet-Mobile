package dev.spinet.update

import dev.spinet.local.PlatformAppContext

actual fun createUpdateInstaller(): UpdateInstaller? =
    PlatformAppContext.appOrNull?.let { AndroidUpdateInstallerAdapter(it) }
