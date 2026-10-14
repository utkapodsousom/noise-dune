package ru.noisedune.dsp

/**
 * Brown noise (-6 dB/octave): white noise through a single integrator.
 *
 * The integrator is leaky — a one-pole low pass rather than a running sum,
 * which would random-walk away from zero without bound.
 *
 * The high pass is the interesting part. Brown is the voice that limits how
 * loud the app can go, because most of its energy is infrasonic: inaudible, yet
 * it dictates the peak level and therefore how much gain the whole chain can
 * take before clipping. Moving the corner from 8 Hz to 30 Hz and making it
 * second order raised the loudest clip-free A-weighted level this voice can
 * reach from -33.4 to -29.4 dBFS — 4 dB of real loudness bought back from
 * frequencies no one can hear and no phone speaker can reproduce. The slope
 * stays at -5.97 dB/octave across 100 Hz - 10 kHz.
 */
class BrownNoise(
    private val sampleRate: Int,
    seed: Long,
    params: Params = Params(),
) : NoiseSource {

    /**
     * The defaults are the measured constants this voice ships with, so
     * `Params()` is the shipped sound exactly; `NoiseTuningTest` fails if one
     * ever drifts.
     *
     * All three are live-tunable, and all three move this voice's peak
     * headroom — which is the whole app's loudness ceiling, not just brown's.
     * Expect to re-trim after touching them.
     */
    data class Params(
        val integratorHz: Double = 30.0,
        val highPassHz: Double = 30.0,
        val highPassQ: Double = 0.707,
    )

    private val rng = Rng(seed)
    private val integrator = OnePole(sampleRate, params.integratorHz)
    private val highPass = Biquad.highPass(sampleRate, params.highPassHz, params.highPassQ)

    override fun render(out: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            out[i] = highPass.process(integrator.process(rng.nextSample()))
        }
    }

    /** Both filters move in place, keeping their state, so nothing clicks. */
    fun retune(params: Params) {
        integrator.setCutoff(params.integratorHz)
        highPass.setHighPass(sampleRate, params.highPassHz, params.highPassQ)
    }
}
