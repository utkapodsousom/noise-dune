package ru.noisedune.dsp

/**
 * Pink noise (-3 dB/octave) by the Voss-McCartney method: a stack of white
 * generators, each updated half as often as the one above it, summed together.
 *
 * McCartney's refinement keeps the cost constant regardless of the row count:
 * the number of trailing zero bits in a sample counter picks the single row that
 * is due this sample, and the sum is maintained incrementally instead of being
 * recomputed. So one row update and two random numbers per sample, whether
 * there are 8 rows or 32.
 *
 * Preferred over a pinking filter (such as Paul Kellett's chain of one-poles)
 * because the row structure is defined in samples, not in hertz — the slope is
 * identical at 44.1 and 48 kHz, where filter coefficients tuned for one rate
 * drift at the other.
 *
 * Measured: -3.09 dB/octave over 100 Hz - 10 kHz, and within 0.1 dB of that in
 * every octave band from 20 Hz to 20 kHz.
 */
class PinkNoise(sampleRate: Int, seed: Long, params: Params = Params()) : NoiseSource {

    /**
     * The defaults are the measured constants this voice ships with, so
     * `Params()` is the shipped sound exactly; `NoiseTuningTest` fails if one
     * ever drifts. They are defaults rather than a companion object because the
     * developer tuning panel needs to vary them by ear.
     */
    data class Params(
        /** 16 rows reach down to about 0.7 Hz at 48 kHz, well below hearing. */
        val rows: Int = 16,

        /**
         * 8 Hz: cuts the measured DC wander from -5.7e-3 to -5.7e-6 and drops
         * sub-20 Hz power from 32 % to 12 %, while leaving the -3 dB/octave
         * slope untouched even when measured from 20 Hz. Pushing it to 30 Hz
         * starts bending the bottom octave (slope -2.85).
         */
        val dcBlockHz: Double = 8.0,
    ) {
        /**
         * Derived, never tuned: the top octave is the per-sample generator, so
         * the sum spans `rows + 1` sources.
         */
        val scale: Float get() = 1f / (rows + 1)
    }

    private val rng = Rng(seed)
    private val rows = FloatArray(params.rows)
    private val scale = params.scale
    private val dcBlocker = DcBlocker(sampleRate, params.dcBlockHz)

    private var runningSum = 0f
    private var counter = 0

    init {
        for (i in rows.indices) {
            rows[i] = rng.nextSample()
            runningSum += rows[i]
        }
    }

    override fun render(out: FloatArray, frames: Int) {
        var sum = runningSum
        var n = counter
        for (i in 0 until frames) {
            n++
            val row = n.countTrailingZeroBits()
            if (row < rows.size) {
                val fresh = rng.nextSample()
                sum += fresh - rows[row]
                rows[row] = fresh
            }
            out[i] = dcBlocker.process((sum + rng.nextSample()) * scale)
        }
        runningSum = sum
        counter = n
    }

    /**
     * Moves what can move without a rebuild.
     *
     * [Params.rows] cannot: it sizes the bank, sets the gain that goes with it
     * and seeds the running sum, so a change there rebuilds the generator and
     * never reaches here. The corner of the DC blocker moves in place, keeping
     * its state, so a slider on it cannot click.
     */
    fun retune(params: Params) = dcBlocker.setCutoff(params.dcBlockHz)
}
