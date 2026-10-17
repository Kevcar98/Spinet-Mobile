package dev.spinet.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.spinet.local.PlatformAppContext

private const val ACTION_CANCEL = "dev.spinet.TRANSFER_CANCEL"

private const val ACTION_STOP = "dev.spinet.TRANSFER_STOP"

private const val CHANNEL_ID = "transfers"
private const val NOTIF_ID = 4210

private const val NOTIF_ID_DONE = 4211

actual object TransferNotifier {
    private fun ctx(): Context? = runCatching { PlatformAppContext.app }.getOrNull()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Downloads & uploads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )
        }
    }

    internal fun build(
        context: Context,
        title: String,
        text: String,
        fraction: Float,
        ongoing: Boolean
    ) = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(ongoing)
        .apply {
            if (ongoing) {
                if (fraction < 0f) setProgress(0, 0, true)
                else setProgress(100, (fraction * 100).toInt().coerceIn(0, 100), false)

                val cancelIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, TransferCancelReceiver::class.java).setAction(ACTION_CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            } else {
                setProgress(0, 0, false)
                setSmallIcon(android.R.drawable.stat_sys_download_done)
            }
        }
        .build()

    @Volatile private var title: String = "Transfer"

    actual fun start(title: String) {
        this.title = title
        val context = ctx() ?: return
        ensureChannel(context)
        val intent = Intent(context, DownloadService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    actual fun update(fraction: Float, text: String) {
        val context = ctx() ?: return
        ensureChannel(context)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID, build(context, title, text, fraction, ongoing = true))
        }
    }

    actual fun finish(text: String, ok: Boolean) {
        val context = ctx() ?: return
        ensureChannel(context)

        stopServiceSafely(context)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID_DONE, build(context, title, text, 1f, ongoing = false))
        }
    }

    actual fun cancel() {
        val context = ctx() ?: return
        stopServiceSafely(context)
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIF_ID) }
    }

    private fun stopServiceSafely(context: Context) {
        val intent = Intent(context, DownloadService::class.java).setAction(ACTION_STOP)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

class TransferCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TransferController.requestCancel()
    }
}

class DownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = TransferNotifier.build(
            this, "Transferring…", "Starting…", -1f, ongoing = true
        )
        startForeground(NOTIF_ID, notification)
        if (intent?.action == ACTION_STOP) {
            @Suppress("DEPRECATION")
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }
}
