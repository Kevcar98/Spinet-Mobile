package dev.spinet.recognition

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import dev.spinet.local.PlatformAppContext
import kotlin.coroutines.resume
import kotlin.math.max

object MicPermissionBridge {
    @Volatile
    var requester: ((onResult: (Boolean) -> Unit) -> Unit)? = null
}

actual fun createAudioCapture(): AudioCapture? = AndroidAudioCapture()

private const val SOURCE_MIC = 0

private class AndroidAudioCapture : AudioCapture {
    override fun sources(): List<CaptureSource> =
        listOf(CaptureSource(id = SOURCE_MIC, name = "Microphone", isSystemAudio = false))

    override suspend fun record(sourceId: Int, seconds: Int): ShortArray? {
        if (!ensurePermission()) return null
        return withContext(Dispatchers.IO) { doRecordMic(seconds) }
    }

    private suspend fun ensurePermission(): Boolean {
        val ctx = PlatformAppContext.app
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) return true
        val requester = MicPermissionBridge.requester ?: return false
        return suspendCancellableCoroutine { cont ->
            requester { granted -> if (cont.isActive) cont.resume(granted) }
        }
    }

    private fun doRecordMic(seconds: Int): ShortArray? {
        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return null
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuf * 2, sampleRate * 2),
            )
        } catch (_: Exception) {
            return null
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        return try {
            recorder.startRecording()
            val out = ShortArray(sampleRate * seconds)
            var off = 0
            while (off < out.size) {
                val n = recorder.read(out, off, out.size - off)
                if (n <= 0) break
                off += n
            }
            recorder.stop()

            if (off >= sampleRate) out.copyOf(off) else null
        } catch (_: Exception) {
            null
        } finally {
            recorder.release()
        }
    }
}
