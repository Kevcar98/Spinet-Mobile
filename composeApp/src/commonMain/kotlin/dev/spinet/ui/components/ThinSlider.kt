package dev.spinet.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val THUMB = 14.dp
private val TRACK = 4.dp

private val HIT_HEIGHT = 48.dp

@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
) {
    val density = LocalDensity.current

    val onChange = rememberUpdatedState(onValueChange)
    val onFinished = rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(modifier.height(HIT_HEIGHT)) {
        val widthPx = constraints.maxWidth.toFloat()
        val thumbPx = with(density) { THUMB.toPx() }
        val trackPx = with(density) { TRACK.toPx() }

        fun valueAt(x: Float): Float {
            val travel = (widthPx - thumbPx).coerceAtLeast(1f)
            return ((x - thumbPx / 2f) / travel).coerceIn(0f, 1f)
        }

        val gestures = if (!enabled) {
            Modifier
        } else {
            Modifier
                .pointerInput(widthPx) {
                    detectTapGestures { offset ->
                        onChange.value(valueAt(offset.x))
                        onFinished.value?.invoke()
                    }
                }

                .pointerInput(widthPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset -> onChange.value(valueAt(offset.x)) },
                        onDragEnd = { onFinished.value?.invoke() }
                    ) { change, _ -> onChange.value(valueAt(change.position.x)) }
                }
        }

        Canvas(Modifier.fillMaxSize().then(gestures)) {
            val cy = size.height / 2f
            val radius = thumbPx / 2f
            val cx = radius + (size.width - thumbPx) * value.coerceIn(0f, 1f)

            val dim = if (enabled) 1f else 0.4f
            drawLine(
                color = trackColor.copy(alpha = trackColor.alpha * dim),
                start = Offset(radius, cy),
                end = Offset(size.width - radius, cy),
                strokeWidth = trackPx,
                cap = StrokeCap.Round
            )
            if (enabled) {
                drawLine(
                    color = fillColor,
                    start = Offset(radius, cy),
                    end = Offset(cx, cy),
                    strokeWidth = trackPx,
                    cap = StrokeCap.Round
                )
                drawCircle(color = fillColor, radius = radius, center = Offset(cx, cy))
            }
        }
    }
}
