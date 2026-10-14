package ru.noisedune.dsp

/**
 * White noise: one generator, no filters.
 *
 * The samples are uniformly rather than normally distributed. For a flat
 * spectrum that makes no audible difference — the distribution shapes the
 * crest factor, not the spectrum — and uniform costs one shift and one
 * multiply instead of a Box-Muller pair.
 */
class WhiteNoise(seed: Long) : NoiseSource {

    private val rng = Rng(seed)

    override fun render(out: FloatArray, frames: Int) {
        for (i in 0 until frames) {
            out[i] = rng.nextSample()
        }
    }
}
