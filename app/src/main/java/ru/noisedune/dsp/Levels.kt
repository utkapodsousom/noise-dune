package ru.noisedune.dsp

import kotlin.math.pow
import kotlin.math.tanh

/**
 * Everything about how loud the app gets, in one place.
 *
 * The chain is: generator -> [NoiseType.calibration] -> slider gain -> fade
 * gain -> [softClip]. Calibration equalises loudness between voices; the slider
 * and the fades are plain multiplications on top.
 */
object Levels {

    /**
     * The A-weighted level every voice is calibrated to, at full slider.
     *
     * Set by the worst case: brown reaches -29.4 dBFS(A) before its peaks hit
     * the -3 dBFS ceiling, so that is where all four sit. In practice this puts
     * a phone at full system volume somewhere around 55-60 dBA at one metre —
     * close to the level a nursery actually wants.
     */
    const val TARGET_A_WEIGHTED_DBFS = -29.5

    /** Quietest usable slider position. */
    const val MIN_VOLUME_DB = -48.0

    /** Loudest slider position with the safety cap off. */
    const val MAX_VOLUME_DB = 0.0

    /**
     * Loudest slider position with the cap on, which is the default.
     *
     * A 6 dB ceiling is a guard rail, not a guarantee: the sound pressure that
     * actually reaches a cot depends on the speaker, the system volume and the
     * distance, none of which the app can see. The UI says so rather than
     * implying a measured limit.
     */
    const val CAPPED_VOLUME_DB = -6.0

    /** Fades start here rather than at true silence; see [GainRamp]. */
    const val FADE_FLOOR_DB = -45.0

    /**
     * Maps a 0..1 slider position to an amplitude, spaced evenly in decibels.
     *
     * A slider that is linear in amplitude feels broken — everything audible
     * crowds into the top fifth of the travel, because loudness perception is
     * roughly logarithmic.
     */
    fun sliderToAmplitude(position: Float, capped: Boolean): Float {
        val maxDb = if (capped) CAPPED_VOLUME_DB else MAX_VOLUME_DB
        val clamped = position.coerceIn(0f, 1f)
        val db = MIN_VOLUME_DB + clamped * (maxDb - MIN_VOLUME_DB)
        return dbToAmplitude(db)
    }

    fun dbToAmplitude(db: Double): Float = 10.0.pow(db / 20.0).toFloat()
}

/**
 * Soft knee above 0.9, saturating at 1.0; identity below the knee.
 *
 * This is a safety net, not a sound-shaping stage. With the calibration above,
 * brown peaks at about -2.3 dBFS, and over a whole night its Gaussian tail will
 * graze the knee a handful of times. Catching those few samples smoothly is
 * inaudible, where letting them wrap or hard-clip would be a click in a quiet
 * room at 3 a.m.
 *
 * `tanh` is the real libm one rather than a polynomial approximation: the branch
 * is taken for a vanishing fraction of samples, so its cost does not matter,
 * and the cheap rational approximations overshoot 1.0 for large inputs — which
 * would defeat the single thing this function exists to guarantee.
 */
fun softClip(x: Float): Float {
    val magnitude = if (x < 0f) -x else x
    if (magnitude <= SOFT_CLIP_KNEE) return x
    val over = (magnitude - SOFT_CLIP_KNEE) / (1f - SOFT_CLIP_KNEE)
    val shaped = SOFT_CLIP_KNEE + (1f - SOFT_CLIP_KNEE) * tanh(over)
    return if (x < 0f) -shaped else shaped
}

private const val SOFT_CLIP_KNEE = 0.9f
