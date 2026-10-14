package ru.noisedune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * One palette, always dark.
 *
 * This app gets used in a dark room at 3 a.m. with a sleeping child in it, so
 * the usual rules are inverted: nothing is white, nothing is saturated, and
 * `isSystemInDarkTheme` is deliberately ignored — a light theme would be a
 * flashbulb. Dynamic colour is off for the same reason; it could hand us a
 * bright accent.
 *
 * The values come from the "Nocturne Sleep Engine" design system in
 * `docs/stitch-prompts.md`, but only the ones that were actually painted: that
 * system also declares `on-surface: #e2e2e2` and `primary: #ffb86a` as text
 * colours and then never uses them, because at those levels text glows in a
 * dark nursery. What it really renders text in is [NocturneDim] and its two
 * warmer relatives, and that is what is carried over here.
 */

/** The one true background. On OLED these pixels are off and emit nothing. */
private val Void = Color(0xFF000000)

/** The orb's own surface — just enough lift to read as an object, not a hole. */
private val OrbFill = Color(0xFF141414)

/** Recessed chrome: header buttons, the volume track. */
private val Recess = Color(0xFF1B1B1B)

/** The selected state of a pill or chip. */
private val Raised = Color(0xFF2A2A2A)

/** Standard foreground. Warm grey rather than neutral — it reads as candlelight. */
private val NocturneDim = Color(0xFFA08E7D)

/** Reserved for the one line that carries live time. */
private val NocturneBright = Color(0xFFD7C3B1)

/** The warmest foreground: the voice name inside the orb, a selected pill. */
private val Ember = Color(0xFFF8BA7D)

/** Accent fills — the presence dot, the volume fill, a switch that is on. */
private val Filament = Color(0xFFD98E32)

/** The lit edge of the waveform and the halo. Never used for text. */
private val FilamentLight = Color(0xFFFFB86A)

private val Palette = darkColorScheme(
    background = Void,
    onBackground = NocturneDim,
    surface = Void,
    onSurface = NocturneBright,
    surfaceVariant = Recess,
    onSurfaceVariant = NocturneDim,
    surfaceContainerLowest = Void,
    surfaceContainerLow = Recess,
    surfaceContainer = OrbFill,
    surfaceContainerHigh = Raised,
    surfaceContainerHighest = Color(0xFF353535),
    // Warm amber rather than blue: long-wavelength light interferes least with
    // dark adaptation, so glancing at the screen costs less night vision.
    primary = Filament,
    onPrimary = Color(0xFF492900),
    primaryContainer = Color(0xFF3A2416),
    onPrimaryContainer = Ember,
    secondary = Ember,
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Raised,
    onSecondaryContainer = Ember,
    tertiary = FilamentLight,
    outline = NocturneDim,
    outlineVariant = Color(0xFF524437),
    error = Color(0xFF8A4A42),
    onError = Color(0xFF1A0A08),
)

/**
 * The scale from the design system, set in the system sans.
 *
 * The system has no `INTERNET` permission, so downloadable Google Fonts cannot
 * work at runtime, and bundling Space Grotesk and Plus Jakarta Sans would cost
 * several hundred kilobytes for a four-size scale. What actually carries the
 * look is the restraint — weights capped at 500, generous tracking on the
 * uppercase micro-labels — and that survives the substitution.
 */
private val DimTypography = Typography(
    // The voice name inside the orb.
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.025.em,
    ),
    // Settings rows: the thing being switched.
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    // The live line under the orb, and timer chips.
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.02.em,
    ),
    // The header label, the metadata line, the second line of a settings row.
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.04.em,
    ),
    // Ornament only — never a control, never essential text.
    labelSmall = TextStyle(
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.06.em,
    ),
)

@Composable
fun NoiseDuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Palette,
        typography = DimTypography,
        content = content,
    )
}
