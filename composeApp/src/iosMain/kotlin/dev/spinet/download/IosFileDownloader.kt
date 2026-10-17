package dev.spinet.download

class IosFileDownloader : FileDownloader {
    override suspend fun downloadAudio(
        url: String,
        title: String,
        artist: String,
        artworkUrl: String,
        subfolder: String,
        onProgress: (Float) -> Unit
    ): DownloadResult {
        return DownloadResult.Error("Download not implemented on iOS yet")
    }
}
