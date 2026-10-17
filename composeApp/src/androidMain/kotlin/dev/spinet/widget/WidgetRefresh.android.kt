package dev.spinet.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dev.spinet.local.PlatformAppContext
import java.util.concurrent.atomic.AtomicBoolean

private val refreshScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

private val refreshPending = AtomicBoolean(false)

private const val REFRESH_DEBOUNCE_MS = 350L

actual fun logWidget(message: String) = WidgetLog.d(message)

private suspend fun repaint(
    context: Context,
    receiver: Class<*>,
    widget: GlanceAppWidget
): Int {
    val ids = AppWidgetManager.getInstance(context)
        ?.getAppWidgetIds(ComponentName(context, receiver))
        ?: return 0
    if (ids.isEmpty()) return 0

    val manager = GlanceAppWidgetManager(context)
    var drawn = 0
    for (id in ids) {
        val glanceId = runCatching { manager.getGlanceIdBy(id) }.getOrNull()
        if (glanceId == null) {
            WidgetLog.w("refresh: widget id $id has no Glance id")
            continue
        }
        widget.update(context, glanceId)
        drawn++
    }
    return drawn
}

internal suspend fun performWidgetRefresh(context: Context): Int {
    var painted = 0
    WidgetLog.guard("render state refresh") {
        WidgetRenderState.refresh(context) {
            painted = WidgetLog.guard("widget refresh") {
                repaint(context, NowPlayingWidgetReceiver::class.java, NowPlayingWidget())
            } ?: painted
        }
    }
    WidgetLog.d("refresh: redrew $painted widget(s)")
    return painted
}

actual fun refreshWidgets() {
    val context = PlatformAppContext.appOrNull
    if (context == null) {
        WidgetLog.w("refresh skipped: no app context yet")
        return
    }

    if (!refreshPending.compareAndSet(false, true)) return
    refreshScope.launch {
        delay(REFRESH_DEBOUNCE_MS)

        refreshPending.set(false)
        performWidgetRefresh(context)
    }
}

internal fun runOffReceiver(label: String, block: suspend () -> Unit) {
    refreshScope.launch { WidgetLog.guard(label) { block() } }
}
