package dev.spinet.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import dev.spinet.local.PlatformAppContext

private suspend fun transportOrHeadless(
    context: Context,
    label: String,
    onSession: suspend (Context) -> Boolean,
    headlessAction: String
) {
    WidgetLog.d("$label tapped")
    val bySession = withinActionBudget("$label transport") {
        WidgetLog.guard("$label transport") { onSession(context) } ?: false
    } ?: false
    if (bySession) {
        WidgetLog.d("$label handled by the live session")

        delay(TRANSPORT_SETTLE_MS)
        refreshWidgets()
    } else {
        WidgetLog.d("$label has no live session, handing it to the playback service")

        WidgetPlaybackService.start(context, headlessAction)
    }
}

private const val TRANSPORT_SETTLE_MS = 400L

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, id: GlanceId, parameters: ActionParameters) {
        PlatformAppContext.ensure(context)
        WidgetLog.d("play/pause tapped")

        val live = withinActionBudget("play/pause: read session") {
            WidgetLog.guard("play/pause: read session") { WidgetTransport.currentState(context) }
        }

        if (live != null && live.playWhenReady) {
            WidgetLog.d("play/pause: pausing the live session")
            WidgetLog.guard("play/pause: pause") { WidgetTransport.togglePlayPause(context) }

            delay(TRANSPORT_SETTLE_MS)
            refreshWidgets()
            return
        }

        WidgetLog.d("play/pause: starting playback, checking the server first")
        WidgetPlaybackService.start(context, WidgetPlaybackService.ACTION_RESUME)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, id: GlanceId, parameters: ActionParameters) {
        PlatformAppContext.ensure(context)
        transportOrHeadless(
            context,
            "next",
            onSession = WidgetTransport::next,
            headlessAction = WidgetPlaybackService.ACTION_NEXT
        )
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, id: GlanceId, parameters: ActionParameters) {
        PlatformAppContext.ensure(context)
        transportOrHeadless(
            context,
            "previous",
            onSession = WidgetTransport::previous,
            headlessAction = WidgetPlaybackService.ACTION_PREVIOUS
        )
    }
}

private suspend fun <T> withinActionBudget(label: String, block: suspend () -> T): T? {
    val boxed = withTimeoutOrNull(ACTION_BUDGET_MS) { Box(block()) }
    if (boxed == null) {
        WidgetLog.w("$label ran out of time (${ACTION_BUDGET_MS}ms)")
        return null
    }
    return boxed.value
}

private class Box<T>(val value: T)

private const val ACTION_BUDGET_MS = 8_000L
