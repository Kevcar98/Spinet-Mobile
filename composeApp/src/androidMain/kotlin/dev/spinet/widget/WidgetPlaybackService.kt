package dev.spinet.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import dev.spinet.local.PlatformAppContext

class WidgetPlaybackService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val lock = Mutex()

    private var running = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground()
        PlatformAppContext.ensure(this)

        val action = intent?.action
        if (action == null) {
            finishOne()
            return START_NOT_STICKY
        }

        running++
        scope.launch {
            WidgetLog.guard("widget playback service: $action") {
                lock.withLock {
                    val started = when (action) {
                        ACTION_RESUME -> HeadlessPlayback.resume(this@WidgetPlaybackService, awaitStart = true)
                        ACTION_NEXT -> HeadlessPlayback.next(this@WidgetPlaybackService, awaitStart = true)
                        ACTION_PREVIOUS -> HeadlessPlayback.previous(this@WidgetPlaybackService, awaitStart = true)
                        else -> {
                            WidgetLog.w("widget playback service: unknown action '$action'")
                            false
                        }
                    }
                    if (!started) WidgetLog.w("widget playback service: '$action' did not start playback")
                }
            }
            refreshWidgets()
            finishOne()
        }
        return START_NOT_STICKY
    }

    private fun finishOne() {
        running = (running - 1).coerceAtLeast(0)
        if (running > 0) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Spinet")
            .setContentText("Starting playback\u2026")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
        }.onFailure {
            WidgetLog.e("widget playback service: the platform refused the foreground start", it)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Widget playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_RESUME = "dev.spinet.widget.RESUME"
        const val ACTION_NEXT = "dev.spinet.widget.NEXT"
        const val ACTION_PREVIOUS = "dev.spinet.widget.PREVIOUS"

        private const val CHANNEL_ID = "widget_playback"
        private const val NOTIF_ID = 4220

        fun start(context: Context, action: String) {
            val intent = Intent(context.applicationContext, WidgetPlaybackService::class.java)
                .setAction(action)
            runCatching {
                ContextCompat.startForegroundService(context.applicationContext, intent)
            }.onFailure {
                WidgetLog.e("could not start the widget playback service", it)
            }
        }
    }
}
