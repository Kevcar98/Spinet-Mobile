package dev.spinet.recognition

enum class IdentifyPhase { IDLE, LISTENING, MATCHING, RESULT, NO_MATCH, ERROR }

data class CaptureSource(
    val id: Int,
    val name: String,

    val isSystemAudio: Boolean,
)

interface AudioCapture {
    fun sources(): List<CaptureSource>

    suspend fun record(sourceId: Int, seconds: Int): ShortArray?
}

expect fun createAudioCapture(): AudioCapture?

data class RecognizedSong(
    val title: String,
    val artist: String,
    val coverUrl: String?,
)
