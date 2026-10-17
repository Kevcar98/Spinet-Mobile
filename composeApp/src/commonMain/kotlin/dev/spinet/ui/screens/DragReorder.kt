package dev.spinet.ui.screens

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex

class ReorderState {
    var draggingIndex by mutableStateOf<Int?>(null)
    var offsetY by mutableStateOf(0f)

    val heights = mutableStateMapOf<Int, Int>()
}

@Composable
fun rememberReorderState(): ReorderState = remember { ReorderState() }

fun Modifier.reorderableItem(state: ReorderState, index: Int): Modifier =
    this.onSizeChanged { state.heights[index] = it.height }
        .graphicsLayer {
            translationY = if (state.draggingIndex == index) state.offsetY else 0f
            shadowElevation = if (state.draggingIndex == index) 12f else 0f
        }
        .zIndex(if (state.draggingIndex == index) 1f else 0f)

@Composable
fun Modifier.reorderHandle(
    state: ReorderState,
    index: Int,
    itemCount: Int,
    key: Any,
    onMove: (Int, Int) -> Unit
): Modifier {
    val currentIndex by rememberUpdatedState(index)
    return this.pointerInput(key) {
        detectDragGestures(
            onDragStart = { state.draggingIndex = currentIndex; state.offsetY = 0f },
            onDragEnd = { state.draggingIndex = null; state.offsetY = 0f },
            onDragCancel = { state.draggingIndex = null; state.offsetY = 0f },
            onDrag = { change, amount ->
                change.consume()
                state.offsetY += amount.y
                val cur = state.draggingIndex ?: return@detectDragGestures
                val h = state.heights[cur] ?: return@detectDragGestures
                when {
                    state.offsetY > h / 2 && cur < itemCount - 1 -> {
                        onMove(cur, cur + 1); state.draggingIndex = cur + 1; state.offsetY -= h
                    }
                    state.offsetY < -h / 2 && cur > 0 -> {
                        onMove(cur, cur - 1); state.draggingIndex = cur - 1; state.offsetY += h
                    }
                }
            }
        )
    }
}

@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}
