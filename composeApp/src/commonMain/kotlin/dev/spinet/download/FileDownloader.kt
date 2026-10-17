package dev.spinet.download

sealed interface DownloadResult {
    data class Success(val location: String) : DownloadResult
    data class Error(val message: String) : DownloadResult
}

interface FileDownloader {
    suspend fun downloadAudio(
        url: String,
        title: String,
        artist: String = "",
        artworkUrl: String = "",

        subfolder: String = "",
        onProgress: (Float) -> Unit = {}
    ): DownloadResult

    suspend fun downloadRaw(
        url: String,
        title: String,
        artist: String = "",
        ext: String = "m4a",
        mime: String = "audio/mp4",
        subfolder: String = "",

        artworkUrl: String = "",
        onProgress: (Float) -> Unit = {}
    ): DownloadResult = DownloadResult.Error("Raw download not supported on this platform")

    suspend fun tagAudio(
        bytes: ByteArray,
        title: String,
        artist: String,
        ext: String,
        artworkUrl: String = ""
    ): ByteArray = bytes

    suspend fun saveLocalArtwork(
        fileKey: String,
        title: String,
        artist: String,
        artworkUrl: String
    ): Boolean = false

    suspend fun saveRepairedAudio(
        bytes: ByteArray,
        title: String,
        artist: String,
        subfolder: String = ""
    ): Boolean = false
}
