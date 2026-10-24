package ru.noisedune.ui.dev

import ru.noisedune.dsp.NoiseTuning
import ru.noisedune.dsp.NoiseType
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * One tunable number, described well enough for the panel to render a slider
 * for it and write the result back.
 *
 * Everything is carried as a [Double] and narrowed in [set], so the slider code
 * stays single-typed instead of growing a branch per parameter type.
 */
internal class DevParam(
    val label: String,
    private val min: Double,
    private val max: Double,
    /**
     * Frequencies and Q values are logarithmic.
     *
     * Pitch is logarithmic, so a linear frequency slider spends most of its
     * travel in an octave nobody is listening to. Q is a ratio, so equal
     * perceptual steps in the width of a resonance are multiplicative too.
     */
    private val logScale: Boolean,
    private val step: Double,
    private val format: (Double) -> String,
    val get: (NoiseTuning) -> Double,
    val set: (NoiseTuning, Double) -> NoiseTuning,
) {

    fun readout(value: Double): String = format(value)

    /** Slider travel 0..1 for a value. */
    fun toPosition(value: Double): Float {
        val clamped = value.coerceIn(min, max)
        val p = if (logScale) {
            ln(clamped / min) / ln(max / min)
        } else {
            (clamped - min) / (max - min)
        }
        return p.toFloat().coerceIn(0f, 1f)
    }

    /**
     * The value for a position on the slider, snapped to [step].
     *
     * Snapped here rather than through `Slider`'s own `steps`, which divides the
     * travel evenly and so quantises unevenly once the travel is logarithmic.
     * The final rounding to six places keeps a snapped value exactly equal to
     * the factory constant it landed on — without it, 1.1 comes back as
     * 1.1000000000000001 and every comparison with the shipped number says the
     * parameter has been edited when it has not.
     */
    fun fromPosition(position: Float): Double {
        val p = position.coerceIn(0f, 1f).toDouble()
        val raw = if (logScale) min * (max / min).pow(p) else min + p * (max - min)
        val snapped = round(raw / step) * step
        return round(snapped * ROUNDING) / ROUNDING
    }

    private companion object {
        const val ROUNDING = 1_000_000.0
    }
}

// ----------------------------------------------------------------- formatting

private fun hz(value: Double): String = if (value < 1000.0) {
    String.format(Locale.ROOT, "%.0f Hz", value)
} else {
    String.format(Locale.ROOT, "%.2f kHz", value / 1000.0)
}

private fun plain(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

/**
 * A bare multiplier, with its decibel value alongside.
 *
 * Both, because the number in the source is a multiplier but the quantity the
 * ear is judging is a level.
 */
private fun gain(value: Double): String = if (value <= 0.0) {
    "0.00 (silent)"
} else {
    String.format(Locale.ROOT, "%.2f (%+.1f dB)", value, 20.0 * kotlin.math.log10(value))
}

private fun trim(value: Double): String = String.format(Locale.ROOT, "%+.1f dB", value)

// -------------------------------------------------------------------- tables

/**
 * The trim, which every voice has — including white, which has nothing else.
 *
 * +-12 dB is wide enough to rebalance the voices against each other by ear and
 * narrow enough that no setting can undo the calibration entirely.
 */
internal fun trimParam(type: NoiseType) = DevParam(
    label = "trim",
    min = -12.0,
    max = 12.0,
    logScale = false,
    step = 0.1,
    format = ::trim,
    get = { it.trimDb(type).toDouble() },
    set = { tuning, value -> tuning.withTrimDb(type, value.toFloat()) },
)

internal val PINK_PARAMS = listOf(
    DevParam(
        label = "rows",
        min = 8.0,
        max = 20.0,
        logScale = false,
        step = 1.0,
        format = { "${it.roundToInt()} rows" },
        get = { it.pink.rows.toDouble() },
        set = { t, v -> t.copy(pink = t.pink.copy(rows = v.roundToInt())) },
    ),
    DevParam(
        label = "dcBlockHz",
        min = 1.0,
        // Well clear of where the blocker's first-order pole approximation
        // stops being faithful.
        max = 60.0,
        logScale = true,
        step = 0.5,
        format = ::hz,
        get = { it.pink.dcBlockHz },
        set = { t, v -> t.copy(pink = t.pink.copy(dcBlockHz = v)) },
    ),
)

internal val BROWN_PARAMS = listOf(
    DevParam(
        label = "integratorHz",
        min = 5.0,
        max = 200.0,
        logScale = true,
        step = 1.0,
        format = ::hz,
        get = { it.brown.integratorHz },
        set = { t, v -> t.copy(brown = t.brown.copy(integratorHz = v)) },
    ),
    DevParam(
        label = "highPassHz",
        min = 10.0,
        max = 200.0,
        logScale = true,
        step = 1.0,
        format = ::hz,
        get = { it.brown.highPassHz },
        set = { t, v -> t.copy(brown = t.brown.copy(highPassHz = v)) },
    ),
    DevParam(
        label = "highPassQ",
        min = 0.3,
        max = 3.0,
        logScale = true,
        step = 0.01,
        format = ::plain,
        get = { it.brown.highPassQ },
        set = { t, v -> t.copy(brown = t.brown.copy(highPassQ = v)) },
    ),
)

internal val SHUSH_PARAMS = listOf(
    DevParam(
        label = "hpHz",
        min = 200.0,
        max = 3000.0,
        logScale = true,
        step = 10.0,
        format = ::hz,
        get = { it.shush.hpHz },
        set = { t, v -> t.copy(shush = t.shush.copy(hpHz = v)) },
    ),
    DevParam(
        label = "f1Hz",
        min = 1500.0,
        max = 6000.0,
        logScale = true,
        step = 25.0,
        format = ::hz,
        get = { it.shush.f1Hz },
        set = { t, v -> t.copy(shush = t.shush.copy(f1Hz = v)) },
    ),
    DevParam(
        label = "f1Q",
        min = 0.5,
        // Up to 20 because the recorded shush measured Q 18; above that the
        // band is a whistle by any standard.
        max = 20.0,
        logScale = true,
        step = 0.05,
        format = ::plain,
        get = { it.shush.f1Q },
        set = { t, v -> t.copy(shush = t.shush.copy(f1Q = v)) },
    ),
    DevParam(
        label = "f2Hz",
        min = 2000.0,
        max = 9000.0,
        logScale = true,
        step = 25.0,
        format = ::hz,
        get = { it.shush.f2Hz },
        set = { t, v -> t.copy(shush = t.shush.copy(f2Hz = v)) },
    ),
    DevParam(
        label = "f2Q",
        min = 0.5,
        max = 8.0,
        logScale = true,
        step = 0.05,
        format = ::plain,
        get = { it.shush.f2Q },
        set = { t, v -> t.copy(shush = t.shush.copy(f2Q = v)) },
    ),
    DevParam(
        label = "upperFormantGain",
        min = 0.0,
        max = 1.5,
        logScale = false,
        step = 0.01,
        format = ::gain,
        get = { it.shush.upperFormantGain.toDouble() },
        set = { t, v -> t.copy(shush = t.shush.copy(upperFormantGain = v.toFloat())) },
    ),
    DevParam(
        label = "lpHz",
        min = 3000.0,
        // 18 kHz, not 20: the debug build runs at whatever rate the device
        // reports, and at 44.1 kHz a corner any higher pushes the biquad's
        // cos(w0) towards -1. Capping it here is simpler than threading the
        // sample rate into the panel.
        max = 18000.0,
        logScale = true,
        step = 100.0,
        format = ::hz,
        get = { it.shush.lpHz },
        set = { t, v -> t.copy(shush = t.shush.copy(lpHz = v)) },
    ),
    DevParam(
        label = "lpQ",
        min = 0.5,
        max = 8.0,
        logScale = true,
        step = 0.05,
        format = ::plain,
        get = { it.shush.lpQ },
        set = { t, v -> t.copy(shush = t.shush.copy(lpQ = v)) },
    ),
)

/** The parameters of [type], in the order the panel shows them. */
internal fun paramsFor(type: NoiseType): List<DevParam> = when (type) {
    NoiseType.WHITE -> emptyList()
    NoiseType.PINK -> PINK_PARAMS
    NoiseType.BROWN -> BROWN_PARAMS
    NoiseType.SHUSH -> SHUSH_PARAMS
}
