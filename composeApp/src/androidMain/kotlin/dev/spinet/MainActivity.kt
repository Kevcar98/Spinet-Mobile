package dev.spinet

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.spinet.audio.AndroidAudioPlayer
import dev.spinet.download.AndroidFileDownloader
import dev.spinet.local.PlatformAppContext
import dev.spinet.util.AppLog
import dev.spinet.recognition.MicPermissionBridge
import dev.spinet.widget.WidgetRefreshWorker

class MainActivity : ComponentActivity() {
    private lateinit var audioPlayer: AndroidAudioPlayer

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {  }

    private var micPermissionCallback: ((Boolean) -> Unit)? = null

    private val micPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micPermissionCallback?.invoke(granted)
            micPermissionCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        PlatformAppContext.app = applicationContext
        AppLog.start(applicationContext)

        WidgetRefreshWorker.ensureScheduled(applicationContext)
        MicPermissionBridge.requester = { onResult ->
            micPermissionCallback = onResult
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        requestNotificationPermissionIfNeeded()
        audioPlayer = AndroidAudioPlayer(this)
        val fileDownloader = AndroidFileDownloader(this)

        setContent {
            App(
                audioPlayer = audioPlayer,
                fileDownloader = fileDownloader
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        audioPlayer.release()
        super.onDestroy()
    }
}
