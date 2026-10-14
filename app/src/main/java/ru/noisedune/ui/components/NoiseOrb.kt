package ru.noisedune.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The one control on the screen: a breathing circle you tap to start and stop.
 *
 * It carries its own labels rather than deferring to a caption underneath,
 * because in the dark the circle is the only thing the eye finds, and anything
 * written outside it is written somewhere nobody looks. The halo behind it is
 * drawn as a radial gradient rather than a blurred shape: a 40 dp blur every
 * frame is expensive, and at these opacities the gradient is indistinguishable.
 */
@Composable
fun NoiseOrb(
    /** The voice name, shown large inside the circle. */
    title: String,
    /** One word for what playback is doing. Rendered in caps. */
    state: String,
    /** Drives the halo and the waveform. False leaves the orb still and unlit. */
    alive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    diameter: Dp = DEFAULT_DIAMETER,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val breath = rememberBreath(alive)

    // Fades the warmth in and out instead of switching it, so starting playback
    // looks like something warming up rather than a light coming on.
    val warmth by animateFloatAsState(
        targetValue = if (alive) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "orbWarmth",
    )

    Box(
        modifier = modifier.size(diameter * HALO_SCALE),
        contentAlignment = Alignment.Center,
    ) {
        if (warmth > 0.01f) {
            Canvas(Modifier.fillMaxSize()) {
                // Read inside the draw lambda, never in the composable body:
                // otherwise the orb — Surface, Column and two Texts — would
                // recompose sixty times a second instead of just repainting.
                val phase = breath.floatValue
                val radius = size.minDimension / 2f

                // The wide, slow halo.
                val haloRadius = radius * (1f + sin(phase) * 0.05f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to colors.primary.copy(alpha = (0.10f + sin(phase) * 0.06f) * warmth),
                        0.55f to colors.primary.copy(alpha = 0.05f * warmth),
                        1f to Color.Transparent,
                        center = center,
                        radius = haloRadius,
                    ),
                    radius = haloRadius,
                )

                // A second, tighter ring a little out of step with it, which is
                // what stops the whole thing pulsing like a single indicator.
                // Peaks inside its own radius and fades to nothing at the edge.
                // Peaking at 1.0 instead puts the brightest pixels on the
                // boundary, which draws a hard outline rather than a glow.
                val ringRadius = radius * 0.95f * (1f + sin(phase + 0.5f) * 0.08f)
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        0.82f to colors.tertiary.copy(alpha = 0.06f * warmth),
                        1f to Color.Transparent,
                        center = center,
                        radius = ringRadius,
                    ),
                    radius = ringRadius,
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(diameter)
                .semantics {
                    this.contentDescription = contentDescription
                    role = Role.Button
                },
            shape = CircleShape,
            color = colors.surfaceContainer,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        ) {
            // Everything inside shrinks with the circle. Landscape on a short
            // phone drops the orb to about 110 dp, and type that stayed at its
            // full size there broke out of the ring: "Розовый шум" wrapped to
            // "Розов / ый" and lost a whole word to maxLines. Russian is the
            // worse case — the words are longer than the English ones.
            val scale = (diameter / DEFAULT_DIAMETER).coerceAtMost(1f)
            // Floored rather than scaled all the way down: this is the label on
            // the only control here, and past a point a smaller circle is not
            // worth a name you cannot read.
            val titleSize = (TITLE_SIZE_SP * scale).coerceAtLeast(MIN_TITLE_SIZE_SP).sp
            Column(
                modifier = Modifier.padding(horizontal = 20.dp * scale),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Waveform(phase = breath, warmth = warmth, scale = scale)
                Spacer(Modifier.height(8.dp * scale))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = titleSize,
                        lineHeight = (titleSize.value * LINE_HEIGHT_RATIO).sp,
                    ),
                    color = lerpToDim(colors.secondary, colors.onSurfaceVariant, warmth),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    // So a name that still will not fit is visibly cut rather
                    // than quietly missing its last word.
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Six bars rising and falling out of phase with each other. */
@Composable
private fun Waveform(phase: FloatState, warmth: Float, scale: Float) {
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.size(width = WAVEFORM_WIDTH * scale, height = WAVEFORM_HEIGHT * scale)) {
        val radians = phase.floatValue
        val barWidth = 4.dp.toPx() * scale
        val gap = 6.dp.toPx() * scale
        val minHeight = 4.dp.toPx() * scale
        val span = 14.dp.toPx() * scale
        val base = 8.dp.toPx() * scale
        val radius = barWidth / 2f

        BAR_ALPHAS.forEachIndexed { index, alpha ->
            val height = if (warmth > 0.01f) {
                (base + sin(radians + index * 0.8f) * span * warmth).coerceAtLeast(minHeight)
            } else {
                minHeight
            }
            val left = index * (barWidth + gap)
            drawRoundRect(
                color = colors.tertiary.copy(alpha = alpha * (0.35f + 0.65f * warmth)),
                topLeft = Offset(left, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}

/**
 * The breathing phase, in radians, advancing only while [running].
 *
 * One cycle takes about three seconds — sleeping breath, not a spinner. As with
 * the dune field, the phase is accumulated rather than derived from the frame
 * clock so that pausing and resuming is continuous.
 */
@Composable
private fun rememberBreath(running: Boolean): FloatState {
    val phase = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previousNanos = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { nanos ->
                if (previousNanos != 0L) {
                    val delta = ((nanos - previousNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    phase.floatValue =
                        (phase.floatValue + delta * BREATH_RADIANS_PER_SECOND) % (2f * PI.toFloat())
                }
                previousNanos = nanos
            }
        }
    }
    return phase
}

/** Warm when playing, plain grey when not, without a hard switch between. */
private fun lerpToDim(warm: Color, dim: Color, warmth: Float): Color = Color(
    red = dim.red + (warm.red - dim.red) * warmth,
    green = dim.green + (warm.green - dim.green) * warmth,
    blue = dim.blue + (warm.blue - dim.blue) * warmth,
)

private val BAR_ALPHAS = listOf(0.70f, 0.80f, 0.90f, 0.75f, 0.80f, 0.60f)

private val DEFAULT_DIAMETER = 192.dp
private val WAVEFORM_HEIGHT = 28.dp
/** Six 4 dp bars with 6 dp between them. */
private val WAVEFORM_WIDTH = 54.dp

/** How much room the halo needs around the circle itself. */
private const val HALO_SCALE = 1.34f

/** The voice name at the orb's full size, in sp. */
private const val TITLE_SIZE_SP = 22f

/** Below this it stops being readable at arm's length in the dark. */
private const val MIN_TITLE_SIZE_SP = 13f

/** Matches the ratio the type scale uses at full size: 30 over 22. */
private const val LINE_HEIGHT_RATIO = 1.36f

/** 2π over roughly three seconds. */
private const val BREATH_RADIANS_PER_SECOND = 2.1f
