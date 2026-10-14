package ru.noisedune.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.noisedune.R

/**
 * A thin horizontal volume track with a quiet and a loud icon at its ends.
 *
 * Drawn rather than assembled from Material parts so that the whole row is the
 * touch target: pressing anywhere jumps there, dragging tracks the finger.
 * There is no thumb — a few-millimetre handle is hopeless in the dark, and
 * without one the entire 48 dp band is grabbable.
 */
@Composable
fun VolumeBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.volume)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_volume_min),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .semantics { contentDescription = description }
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(Unit) {
                    // One gesture loop for press and drag alike. Separate
                    // detectTapGestures and detectHorizontalDragGestures blocks
                    // would compete for the pointer, and the drag slop would
                    // eat the tap.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // Confirmation by touch, so the screen does not have to
                        // be looked at to know the press landed.
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onValueChange(positionToValue(down.position.x, widthPx))
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            onValueChange(positionToValue(change.position.x, widthPx))
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(TRACK_HEIGHT),
            ) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = colors.surfaceVariant, cornerRadius = radius)
                val filled = size.width * value.coerceIn(0f, 1f)
                if (filled > 1f) {
                    drawRoundRect(
                        color = colors.primary,
                        size = Size(filled, size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }

        Icon(
            painter = painterResource(R.drawable.ic_volume_max),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

private val TRACK_HEIGHT = 6.dp

private fun positionToValue(x: Float, widthPx: Int): Float = (x / widthPx).coerceIn(0f, 1f)
