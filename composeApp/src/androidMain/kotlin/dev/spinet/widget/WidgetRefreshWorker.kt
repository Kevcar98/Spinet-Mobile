package dev.spinet.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import dev.spinet.local.PlatformAppContext
import java.util.concurrent.TimeUnit

internal class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PlatformAppContext.ensure(applicationContext)
        runCatching { performWidgetRefresh(applicationContext) }
            .onFailure { WidgetLog.e("periodic refresh failed", it) }

        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "widget_refresh"

        private const val INTERVAL_MINUTES = 30L

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
