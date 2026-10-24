package ru.noisedune.dsp

import kotlin.math.PI
import kotlin.math.sin

/**
 * The voiceless "shhh" — the [ʃ] sound, produced the way a vocal tract produces
 * it: turbulence in a narrow constriction, shaped by the resonance of the cavity
 * in front of it. The vocal folds do not vibrate, so there is no oscillator
 * here, only noise and resonant filters, as in Klatt's parallel formant branch.
 *
 * Centre frequency matters: [ʃ] peaks around 2.5-3.5 kHz because of its long
 * front cavity, while [s] sits at 4-7 kHz. Tuning too high turns a shush into a
 * hiss. Two band passes in parallel give the broad hump rather than the narrow
 * whistle that high-Q filters produce.
 *
 * The shipped numbers were settled by ear on a phone speaker in the dev tuning
 * panel, which pulled the peak up and the hump in from both sides. Measured:
 * spectral peak 3948 Hz, -3 dB from 2902 to 6933 Hz (1/3-octave smoothed), 54
 * dB down below 500 Hz, 11.9 dB down above 10 kHz.
 *
 * The slow amplitude modulation is what keeps it from sounding like a synthetic
 * band-passed hiss — it is the breath behind the sound. Deliberately not
 * rhythmic: a periodic in-out would be something to listen to rather than
 * something to fall asleep through.
 */
class ShushNoise(
    private val sampleRate: Int,
    seed: Long,
    params: Params = Params(),
) : NoiseSource {

    /**
     * The defaults are the measured constants this voice ships with, so
     * `Params()` is the shipped sound exactly; `NoiseTuningTest` fails if one
     * ever drifts. This is the most parameterised voice and the one with most
     * to gain from being judged by ear rather than by its periodogram.
     */
    data class Params(
        /** Nothing useful below the constriction. */
        val hpHz: Double = 1830.0,
        /** Front cavity resonance of [ʃ]. */
        val f1Hz: Double = 4275.0,
        /** Low Q: a hump, not a whistle. */
        val f1Q: Double = 1.0,
        /** Secondary peak, sat just above the first to widen it. */
        val f2Hz: Double = 4425.0,
        val f2Q: Double = 1.05,
        val upperFormantGain: Float = 0.44f,
        /** Roll-off above the peak, soft enough to keep the hump broad. */
        val lpHz: Double = 4700.0,
        /** The breath behind the sound, not a tremolo. */
        val amRateHz: Double = 0.28,
        /** About +-1 dB. */
        val amDepth: Float = 0.12f,
    )

    private val rng = Rng(seed)
    private val highPass = Biquad.highPass(sampleRate, params.hpHz, HP_Q)
    private val lowerFormant = Biquad.bandPass(sampleRate, params.f1Hz, params.f1Q)
    private val upperFormant = Biquad.bandPass(sampleRate, params.f2Hz, params.f2Q)
    private val softTop = OnePole(sampleRate, params.lpHz)

    private var upperFormantGain = params.upperFormantGain
    private var amDepth = params.amDepth
    private var phaseIncrement = phaseIncrement(params.amRateHz)
    private var phase = 0.0

    override fun render(out: FloatArray, frames: Int) {
        var i = 0
        while (i < frames) {
            // The breath gain is recomputed per sub-block instead of per sample:
            // at 0.15 Hz it moves by under 0.0003 over 64 frames, so holding it
            // costs nothing audible and saves a sin() per sample.
            val gain = 1f + amDepth * sin(phase).toFloat()

            // Advanced by the frames this sub-block actually covers, not by a
            // whole SUB_BLOCK: the last one of a buffer is usually short, and
            // crediting it a full block would run the breath fast. Short
            // buffers arrive on every crossfade, so this is not hypothetical.
            val end = minOf(i + SUB_BLOCK, frames)
            phase += phaseIncrement * (end - i)
            if (phase >= TWO_PI) phase -= TWO_PI

            while (i < end) {
                val turbulence = highPass.process(rng.nextSample())
                val resonated = lowerFormant.process(turbulence) +
                    upperFormantGain * upperFormant.process(turbulence)
                out[i] = softTop.process(resonated) * gain
                i++
            }
        }
    }

    /**
     * Every parameter here moves in place.
     *
     * The filters keep their state and the breath keeps its [phase], so the
     * sound bends towards the new setting rather than restarting at it — which
     * is what makes a slider on any of these usable while listening.
     */
    fun retune(params: Params) {
        highPass.setHighPass(sampleRate, params.hpHz, HP_Q)
        lowerFormant.setBandPass(sampleRate, params.f1Hz, params.f1Q)
        upperFormant.setBandPass(sampleRate, params.f2Hz, params.f2Q)
        softTop.setCutoff(params.lpHz)
        upperFormantGain = params.upperFormantGain
        amDepth = params.amDepth
        phaseIncrement = phaseIncrement(params.amRateHz)
    }

    private fun phaseIncrement(rateHz: Double) = 2.0 * PI * rateHz / sampleRate

    private companion object {
        /**
         * The constriction high pass sits below everything audible in this
         * voice, so its Q is a shelf shape with nothing to judge by ear. Not
         * exposed to the tuning panel: one fewer control that cannot help.
         */
        const val HP_Q = 0.707

        /**
         * How often the breath gain is recalculated. A blocking constant, not a
         * parameter of the sound — raising it would make the modulation step
         * audibly, which is a bug rather than a setting, so the tuning panel
         * leaves it alone.
         */
        const val SUB_BLOCK = 64
        const val TWO_PI = 2.0 * PI
    }
}
