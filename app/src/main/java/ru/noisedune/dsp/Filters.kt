package ru.noisedune.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Why the coefficients below are `var` rather than `val`.
 *
 * Each filter can be re-pointed at a new cutoff without being rebuilt, and
 * crucially without discarding its state — `y`, or `x1/x2/y1/y2`. A filter that
 * dropped its state on every change would emit a transient each time, which is
 * exactly what a dragged slider would turn into a stream of clicks. Keeping the
 * state makes the change phase-continuous: it is why a cutoff knob on a
 * synthesiser sounds like a sweep rather than like a gate.
 *
 * This is what lets the developer tuning panel change a parameter with no
 * crossfade and no allocation at all. The cost is a non-final field read per
 * sample, invisible next to the multiplies around it.
 *
 * The honest limit: a *large* jump leaves state that no longer corresponds to
 * the new filter, and a high-Q biquad can ring briefly. At the Q values these
 * voices use (up to 2) and with the panel's throttled steps that is inaudible;
 * the panel's short crossfade covers the pathological full-travel swipe.
 */

/**
 * One-pole low pass. Rolls off at 6 dB/octave above `cutoffHz`, which is the
 * "single integrator" the brown voice is built on.
 */
class OnePole(private val sampleRate: Int, cutoffHz: Double) {

    private var a = coefficient(sampleRate, cutoffHz)
    private var y = 0f

    fun process(x: Float): Float {
        y += a * (x - y)
        return y
    }

    /** Moves the corner, keeping the running output. */
    fun setCutoff(cutoffHz: Double) {
        a = coefficient(sampleRate, cutoffHz)
    }

    private companion object {
        fun coefficient(sampleRate: Int, cutoffHz: Double): Float =
            (1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)).toFloat()
    }
}

/**
 * First-order DC blocker (high pass at `cutoffHz`).
 *
 * Both Voss-McCartney pink and the brown integrator wander below the audible
 * band — pink measured 32 % of its total power under 20 Hz. That energy cannot
 * be heard but it does eat peak headroom and push the speaker cone around, so
 * it is removed before the signal reaches the output.
 */
class DcBlocker(private val sampleRate: Int, cutoffHz: Double) {

    private var r = coefficient(sampleRate, cutoffHz)
    private var x1 = 0f
    private var y1 = 0f

    fun process(x: Float): Float {
        val y = x - x1 + r * y1
        x1 = x
        y1 = y
        return y
    }

    /** Moves the corner, keeping the one-sample history. */
    fun setCutoff(cutoffHz: Double) {
        r = coefficient(sampleRate, cutoffHz)
    }

    private companion object {
        /**
         * A first-order approximation of the pole position, which is why the
         * tuning panel clamps this filter's corner well below Nyquist: the
         * expression goes negative long before it, and is only faithful while
         * the corner stays a small fraction of the sample rate.
         */
        fun coefficient(sampleRate: Int, cutoffHz: Double): Float =
            (1.0 - 2.0 * PI * cutoffHz / sampleRate).toFloat()
    }
}

/**
 * Biquad filter, direct form 1, with coefficients from the RBJ Audio EQ
 * Cookbook. Coefficients are normalised by a0 when they are set, so the hot loop
 * is five multiplies and four adds.
 */
class Biquad private constructor() {

    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    /** Re-points this filter as a band pass, keeping its state. */
    fun setBandPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = (alpha / a0).toFloat()
        b1 = 0f
        b2 = (-alpha / a0).toFloat()
        a1 = (-2.0 * cos(w0) / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    /** Re-points this filter as a high pass, keeping its state. */
    fun setHighPass(sampleRate: Int, freqHz: Double, q: Double) {
        val w0 = 2.0 * PI * freqHz / sampleRate
        val c = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0 = 1.0 + alpha
        b0 = ((1.0 + c) / 2.0 / a0).toFloat()
        b1 = (-(1.0 + c) / a0).toFloat()
        b2 = ((1.0 + c) / 2.0 / a0).toFloat()
        a1 = (-2.0 * c / a0).toFloat()
        a2 = ((1.0 - alpha) / a0).toFloat()
    }

    companion object {

        /** Band pass with constant 0 dB peak gain. */
        fun bandPass(sampleRate: Int, freqHz: Double, q: Double): Biquad =
            Biquad().apply { setBandPass(sampleRate, freqHz, q) }

        /** High pass, 12 dB/octave. */
        fun highPass(sampleRate: Int, freqHz: Double, q: Double): Biquad =
            Biquad().apply { setHighPass(sampleRate, freqHz, q) }
    }
}
