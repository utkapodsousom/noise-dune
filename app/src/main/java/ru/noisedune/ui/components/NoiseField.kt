package ru.noisedune.ui.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The drifting dune field that fills the lower half of the screen.
 *
 * Five silhouettes stacked back to front, each a filled sine profile drifting
 * sideways at its own speed and in the alternating direction. The periods are
 * long on purpose — the fastest layer takes 25 seconds to come round, the
 * slowest 70 — so the field never looks like it is doing anything. You notice
 * that it has moved, not that it is moving.
 *
 * The geometry is carried over from the design mock in
 * `stitch_nightlight_hush.zip`; see `docs/stitch-prompts.md` for the table of
 * constants and where they came from.
 */
@Composable
fun NoiseField(
    /** Fades the whole field out, and stops it being drawn once it is gone. */
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    val phaseSeconds = rememberFieldClock()
    val paths = remember { List(Dunes.size) { Path() } }

    // Below this the field is invisible anyway, and drawing it is pure cost on
    // a screen whose whole point is to be dark and cheap overnight.
    if (intensity <= 0.01f) return

    Canvas(
        modifier
            .fillMaxSize()
            // Applied to the composite rather than to each layer's alpha.
            // Five translucent dunes stacked at 0.45 each do not come to 45% of
            // anything — they come to 1-(1-0.45)^5, which is 95%, and dimming
            // them individually barely dims the field at all.
            .graphicsLayer { alpha = intensity },
    ) {
        // Read inside the draw lambda, never in the composable body: that keeps
        // the per-frame change a redraw instead of a recomposition.
        val seconds = phaseSeconds.floatValue
        Dunes.forEachIndexed { index, dune ->
            drawDune(dune, paths[index], seconds, intensity, isBackmost = index == 0)
        }
    }
}

/**
 * Seconds since the field started moving.
 *
 * Holding the accumulated value rather than reading the frame clock directly
 * means the dunes resume from where they were if this ever stops and restarts,
 * which is the difference between a pause and a glitch.
 */
@Composable
private fun rememberFieldClock(): FloatState {
    val seconds = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var previousNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (previousNanos != 0L) {
                    // Clamped so a dropped frame or a spell in the background
                    // cannot jump the field forward by a visible amount.
                    val delta = ((nanos - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    seconds.floatValue += delta
                }
                previousNanos = nanos
            }
        }
    }
    return seconds
}

private fun DrawScope.drawDune(
    dune: Dune,
    path: Path,
    seconds: Float,
    intensity: Float,
    isBackmost: Boolean,
) {
    val crestY = dune.crestRatio * size.height
    // Flattens towards the crest line as the field quietens, but never all the
    // way: a dead-flat band would read as a UI element rather than terrain.
    val amplitude = dune.amplitudeRatio * size.height * (0.5f + 0.5f * intensity)
    val phase = seconds * (2f * PI.toFloat() / dune.periodSeconds) * dune.direction

    path.reset()
    path.moveTo(0f, size.height)
    var x = 0f
    while (x <= size.width + STEP_PX) {
        val normalisedX = (x / size.width) * 2f * PI.toFloat() * dune.wavelength
        // Two harmonics rather than one: a single sine reads as a graph, the
        // second one makes the crests uneven enough to read as terrain.
        val offset = sin(normalisedX + phase) * amplitude +
            cos(normalisedX * 0.5f - phase * 0.6f) * (amplitude * 0.35f)
        path.lineTo(x.coerceAtMost(size.width), crestY + offset)
        x += STEP_PX
    }
    path.lineTo(size.width, size.height)
    path.close()

    val top = (crestY - amplitude).coerceAtLeast(0f)
    val brush = if (isBackmost) {
        // The one that has to vanish: its top edge is where the field meets the
        // black above it, so it starts fully transparent.
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.35f to dune.colour.copy(alpha = 0.45f),
            1f to dune.colour.copy(alpha = 0.85f),
            startY = top,
            endY = size.height,
        )
    } else {
        Brush.verticalGradient(
            0f to dune.colour.copy(alpha = 0.6f),
            0.18f to dune.colour.copy(alpha = 0.93f),
            1f to dune.colour,
            startY = top,
            endY = size.height,
        )
    }
    drawPath(path, brush)
}

private class Dune(
    /** Where the crest sits, as a fraction of the field's height. */
    val crestRatio: Float,
    val amplitudeRatio: Float,
    /** How many full waves span the width. Deliberately not whole numbers. */
    val wavelength: Float,
    val periodSeconds: Float,
    val direction: Float,
    val colour: Color,
)

/** Back to front. Nothing here is lighter than #353535. */
private val Dunes = listOf(
    Dune(0.18f, 0.12f, 1.15f, 70f, 1f, Color(0xFF0D0D0D)),
    Dune(0.32f, 0.15f, 0.95f, 55f, -1f, Color(0xFF121212)),
    Dune(0.48f, 0.16f, 1.30f, 42f, 1f, Color(0xFF171717)),
    Dune(0.64f, 0.14f, 0.85f, 32f, -1f, Color(0xFF1D1D1D)),
    Dune(0.78f, 0.13f, 1.05f, 25f, 1f, Color(0xFF252525)),
)

/**
 * Sampling step along the width, in pixels.
 *
 * Eight is the mock's value and it holds up: the profiles are slow enough that
 * the straight segments between samples are invisible, and it keeps the work
 * per frame to roughly 150 points per layer at phone widths.
 */
private const val STEP_PX = 8f
