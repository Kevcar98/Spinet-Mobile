package dev.spinet.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.spinet.R
import dev.spinet.local.PlatformAppContext

class NowPlayingWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(170.dp, 100.dp),
            DpSize(250.dp, 130.dp),
            DpSize(320.dp, 170.dp),
            DpSize(320.dp, 280.dp)
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        PlatformAppContext.ensure(context)

        WidgetLog.d("now-playing session started")

        WidgetRefreshWorker.ensureScheduled(context)

        WidgetRenderState.refreshLocal(context)

        refreshWidgets()
        val openApp = WidgetIntents.activityIntent(context, WidgetIntents.ACTION_OPEN)

        provideContent {
            val model by WidgetRenderState.model.collectAsState()
            GlanceTheme {
                NowPlayingContent(model.snapshot, model.art, model.backdrop, model.ring, openApp)
            }
        }
    }
}

@Composable
private fun NowPlayingContent(
    snapshot: WidgetSnapshot,
    art: Bitmap?,
    backdrop: Bitmap?,
    ring: Bitmap?,
    openApp: Intent
) {
    val size = LocalSize.current

    WidgetLog.d("layout: ${size.width} x ${size.height}")

    val onArt = backdrop != null
    val primaryText = if (onArt) ColorProvider(Color.White) else GlanceTheme.colors.onSurface
    val secondaryText =
        if (onArt) ColorProvider(Color(0xCCFFFFFF)) else GlanceTheme.colors.onSurfaceVariant

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)

            .clickable(actionStartActivity(openApp)),
        contentAlignment = Alignment.Center
    ) {
        if (backdrop != null) {
            Image(
                provider = ImageProvider(backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(16.dp)
            )
        }

        NowPlayingBody(size.width, size.height, snapshot, art, ring, primaryText, secondaryText)
    }
}

@Composable
private fun NowPlayingBody(
    width: Dp,
    height: Dp,
    snapshot: WidgetSnapshot,
    art: Bitmap?,
    ring: Bitmap?,
    primaryText: ColorProvider,
    secondaryText: ColorProvider
) {
    val artSize: Dp
    val buttonSize: Dp
    val pad: Dp
    when {
        height >= 240.dp && width >= 300.dp -> { artSize = 96.dp; buttonSize = 64.dp; pad = 16.dp }
        height >= 170.dp -> { artSize = 72.dp; buttonSize = 56.dp; pad = 12.dp }
        height >= 130.dp -> { artSize = 56.dp; buttonSize = 48.dp; pad = 10.dp }
        else -> { artSize = 44.dp; buttonSize = 40.dp; pad = 8.dp }
    }

    val titleLines = if (height >= 170.dp) 2 else 1

    Box(modifier = GlanceModifier.fillMaxSize()) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverArt(art, artSize, secondaryText)
                Spacer(GlanceModifier.width(10.dp))

                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TrackText(snapshot, primaryText, secondaryText, titleLines)
                }
            }
            Controls(snapshot, ring, primaryText, buttonSize)
        }
    }
}

@Composable
private fun TrackText(
    snapshot: WidgetSnapshot,
    primaryText: ColorProvider,
    secondaryText: ColorProvider,
    titleLines: Int
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CentredLine(
            text = snapshot.title.ifBlank { "Nothing playing" },
            maxLines = titleLines,
            style = TextStyle(
                color = primaryText,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
        if (snapshot.artist.isNotBlank()) {
            CentredLine(
                text = snapshot.artist,
                maxLines = 1,
                style = TextStyle(color = secondaryText, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun CentredLine(text: String, maxLines: Int, style: TextStyle) {
    Box(
        modifier = GlanceModifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, maxLines = maxLines, style = style)
    }
}

@Composable
private fun Controls(
    snapshot: WidgetSnapshot,
    ring: Bitmap?,
    tint: ColorProvider,
    buttonSize: Dp
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (snapshot.hasSession) {
            IconButton(
                R.drawable.ic_widget_previous,
                "Previous",
                actionRunCallback<PreviousAction>(),
                tint,
                buttonSize
            )
        }
        PlayPauseButton(snapshot.isPlaying, ring, tint, buttonSize)
        if (snapshot.hasSession) {
            IconButton(
                R.drawable.ic_widget_next,
                "Next",
                actionRunCallback<NextAction>(),
                tint,
                buttonSize
            )
        }
    }
}

@Composable
private fun CoverArt(art: Bitmap?, size: Dp, placeholderTint: ColorProvider) {
    Image(
        provider = if (art != null) ImageProvider(art) else ImageProvider(R.drawable.ic_widget_note),
        contentDescription = "Cover art",
        contentScale = ContentScale.Crop,

        colorFilter = if (art == null) ColorFilter.tint(placeholderTint) else null,
        modifier = GlanceModifier.size(size).cornerRadius(8.dp)
    )
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    ring: Bitmap?,
    tint: ColorProvider,
    buttonSize: Dp
) {
    Box(contentAlignment = Alignment.Center) {
        if (ring != null) {
            Image(
                provider = ImageProvider(ring),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.size(buttonSize)
            )
        }
        IconButton(
            resId = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = if (isPlaying) "Pause" else "Play",
            action = actionRunCallback<PlayPauseAction>(),
            tint = tint,

            buttonSize = buttonSize
        )
    }
}

@Composable
private fun IconButton(
    resId: Int,
    description: String,
    action: Action,
    tint: ColorProvider,
    buttonSize: Dp
) {
    Image(
        provider = ImageProvider(resId),
        contentDescription = description,

        contentScale = ContentScale.Fit,

        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier
            .size(buttonSize)
            .cornerRadius(buttonSize / 2)
            .clickable(action)

            .padding(buttonSize / 5)
    )
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}
