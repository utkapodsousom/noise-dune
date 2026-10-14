package ru.noisedune.dsp

/**
 * xorshift128+ pseudo random generator.
 *
 * Chosen over [java.util.Random] because it allocates nothing, needs no
 * synchronisation and is a handful of integer ops per sample — all three matter
 * on the audio thread, which must never block or trigger a GC.
 *
 * The same seed always produces the same stream, which is what makes the
 * spectral unit tests reproducible.
 */
class Rng(seed: Long) {

    private var s0: Long
    private var s1: Long

    init {
        // splitmix64 spreads a possibly low-entropy seed (1, 2, 3...) over both
        // state words; seeding xorshift directly from a small integer gives a
        // poor first few thousand samples.
        s0 = splitMix64(seed)
        s1 = splitMix64(s0)
        if (s0 == 0L && s1 == 0L) s1 = 1L  // the all-zero state is a fixed point
    }

    fun nextLong(): Long {
        var x = s0
        val y = s1
        s0 = y
        x = x xor (x shl 23)
        s1 = x xor y xor (x ushr 17) xor (y ushr 26)
        return s1 + y
    }

    /** Uniform white noise sample in `[-1, 1)`, using the 24 high bits. */
    fun nextSample(): Float = ((nextLong() ushr 40) / 8388608.0 - 1.0).toFloat()

    private companion object {
        fun splitMix64(seed: Long): Long {
            var z = seed + -0x61c8864680b583ebL  // 0x9E3779B97F4A7C15
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L  // 0xBF58476D1CE4E5B9
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L  // 0x94D049BB133111EB
            return z xor (z ushr 31)
        }
    }
}
