package ru.noisedune.dsp

import kotlin.random.Random

/**
 * A mono noise generator.
 *
 * Rendering is block based rather than sample based so the JIT can keep the
 * filter state in registers across a whole buffer, and so there is no virtual
 * call per sample.
 */
interface NoiseSource {
    /**
     * Fills `out[0 until frames]` with mono samples at a nominal level of
     * roughly unity.
     *
     * Not guaranteed to stay inside `[-1, 1]` — the resonant voices overshoot
     * (shush peaks around 1.17 raw). Bounding the output is [NoiseMixer]'s job,
     * via [NoiseType.calibration] and [softClip]; a generator that clamped
     * itself would distort before the gain stages ever saw it.
     */
    fun render(out: FloatArray, frames: Int)
}

/**
 * The four voices, each with the gain that equalises its **A-weighted** level
 * against the others.
 *
 * Equal amplitude is not equal loudness: at the same RMS, white noise is far
 * louder than brown because its energy sits where the ear is most sensitive.
 * Without this calibration, switching type at night would jolt the volume by
 * 10-20 dB. The constants were measured by integrating an IEC 61672
 * A-weighted Welch periodogram of each generator and solving for the gain that
 * lands every voice on [Levels.TARGET_A_WEIGHTED_DBFS]; see
 * `NoiseCalibrationTest`, which fails if a generator drifts away from it.
 *
 * Measured at 48 kHz. At 44.1 kHz every constant shifts by under 0.4 dB and all
 * in the same direction, so the balance between voices is preserved and a
 * single set of numbers serves both rates.
 */
enum class NoiseType(val calibration: Float) {
    /** Flat spectrum, 0 dB/octave. */
    WHITE(0.112214f),

    /** -3 dB/octave, Voss-McCartney. */
    PINK(0.635854f),

    /** -6 dB/octave, leaky integrator. */
    BROWN(8.243407f),

    /** Voiceless "shhh", energy humped around 4 kHz. */
    SHUSH(0.180302f),
    ;

    /**
     * [tuning] defaults to the shipped constants, so a caller that does not
     * care about the developer tuning panel — which is every caller outside it,
     * including the spectral tests — reads exactly as before.
     */
    fun createSource(
        sampleRate: Int,
        seed: Long = Random.nextLong(),
        tuning: NoiseTuning = NoiseTuning.FACTORY,
    ): NoiseSource = tuning.build(this, sampleRate, seed)
}
