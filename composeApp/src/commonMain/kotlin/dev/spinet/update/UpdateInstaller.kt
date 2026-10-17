package dev.spinet.update

interface UpdateInstaller {
    fun wants(assetName: String): Boolean

    suspend fun install(assetName: String, bytes: ByteArray, checksums: String?): String?

    fun blockedReason(): String? = null

    fun resolveBlock() {}
}
