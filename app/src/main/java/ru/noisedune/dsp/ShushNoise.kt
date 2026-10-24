package ru.noisedune.dsp

/**
 * The voiceless "shhh" — the [ʃ] sound, produced the way a vocal tract produces
 * it: turbulence in a narrow constriction, shaped by the resonance of the cavity
 * in front of it. The vocal folds do not vibrate, so there is no oscillator
 * here, only noise and resonant filters, as in Klatt's parallel formant branch.
 *
 * The shape is copied from a recorded human shush rather than from a textbook.
 * The recording has a narrow main resonance at 2.5 kHz (Q about 18 measured), a
 * dip of about 20 dB at 4-5 kHz, a second peak at 6.3-7.5 kHz some 12 dB down,
 * and a steep fall above 9 kHz. The parameters below were fitted to its
 * spectrum, with the room noise subtracted: 1.7 dB rms across twelfth-octave
 * bands from 1.1 to 13.5 kHz, no band off by more than 3.4 dB below 13 kHz.
 *
 * The second peak is made by the low pass, not by the second formant: a
 * resonant biquad at 6.9 kHz both lifts the peak and steepens the fall above
 * it. A one-pole low pass could do neither, and the best fit that kept one
 * missed the second peak by 8 dB.
 *
 * The level is held dead steady. This voice once carried a slow "breath" of
 * amplitude modulation, and on a band this narrow even ±1 dB was heard as a
 * swell rising and falling every few seconds — something to listen to rather
 * than something to fall asleep through.
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
        val hpHz: Double = 1980.0,
        /** Front cavity resonance of [ʃ]. */
        val f1Hz: Double = 2500.0,
        /** Narrow, as in the recording; Q 18 there, a whistle here. */
        val f1Q: Double = 9.55,
        /** Fills the dip between the main peak and the low pass's peak. */
        val f2Hz: Double = 6575.0,
        val f2Q: Double = 1.95,
        val upperFormantGain: Float = 0.06f,
        /** Corner of the steep top, and the second peak of the voice. */
        val lpHz: Double = 6900.0,
        /** Above 0.707 the low pass peaks at its corner. */
        val lpQ: Double = 3.0,
    )

    private val rng = Rng(seed)
    private val highPass = Biquad.highPass(sampleRate, params.hpHz, HP_Q)
    private val lowerFormant = Biquad.bandPass(sampleRate, params.f1Hz, params.f1Q)
    private val upperFormant = Biquad.bandPass(sampleRate, params.f2Hz, params.f2Q)
    private val top = Biquad.lowPass(sampleRate, params.lpHz, params.lpQ)

    private var upperFormantGain = params.upperFormantGain

    override fun render(out: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            val turbulence = highPass.process(rng.nextSample())
            val resonated = lowerFormant.process(turbulence) +
                upperFormantGain * upperFormant.process(turbulence)
            out[i] = top.process(resonated)
        }
    }

    /**
     * Every parameter here moves in place.
     *
     * The filters keep their state, so the sound bends towards the new setting
     * rather than restarting at it — which is what makes a slider on any of
     * these usable while listening.
     */
    fun retune(params: Params) {
        highPass.setHighPass(sampleRate, params.hpHz, HP_Q)
        lowerFormant.setBandPass(sampleRate, params.f1Hz, params.f1Q)
        upperFormant.setBandPass(sampleRate, params.f2Hz, params.f2Q)
        top.setLowPass(sampleRate, params.lpHz, params.lpQ)
        upperFormantGain = params.upperFormantGain
    }

    private companion object {
        /**
         * The constriction high pass sits below everything audible in this
         * voice, so its Q is a shelf shape with nothing to judge by ear. Not
         * exposed to the tuning panel: one fewer control that cannot help.
         */
        const val HP_Q = 0.707
    }
}
