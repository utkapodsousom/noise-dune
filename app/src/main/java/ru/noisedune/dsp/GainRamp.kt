package ru.noisedune.dsp

import kotlin.math.exp
import kotlin.math.ln

/**
 * A gain that slides to its target exponentially — which is to say linearly in
 * decibels, which is to say linearly as the ear hears it.
 *
 * This single class does all four fades in the app: the 10 second fade-in, the
 * fade-out at the end of the sleep timer, the 50 ms smoothing that keeps slider
 * moves from clicking, and the crossfade between noise types.
 *
 * Why not a straight line in amplitude? A linear 10 second fade-in spends its
 * first seven seconds effectively inaudible and then appears to switch on: half
 * amplitude is only 6 dB down, so almost the whole perceived rise is crammed
 * into the last moment. Stepping by a constant factor per sample instead gives a
 * constant number of decibels per second — one multiply, and it sounds like a
 * fade.
 *
 * True zero has no decibel value, so fades run between [Levels.FADE_FLOOR_DB]
 * and the target and snap the last step. At -45 dB that step is inaudible.
 */
class GainRamp(private val sampleRate: Int, initial: Float = 0f) {

    private var value: Double = initial.toDouble()
    private var target: Float = initial
    private var factor: Double = 1.0
    private var arrivedFlag = false

    /** Frames left before the target is reached; 0 when settled. */
    var framesRemaining: Int = 0
        private set

    val current: Float get() = value.toFloat()

    val isSettled: Boolean get() = framesRemaining == 0

    val isAtZero: Boolean get() = framesRemaining == 0 && value == 0.0

    /** Jump with no ramp. Only safe when nothing is audible yet. */
    fun jumpTo(gain: Float) {
        value = gain.toDouble()
        target = gain
        framesRemaining = 0
        factor = 1.0
        arrivedFlag = false
    }

    fun rampTo(gain: Float, seconds: Float) {
        val frames = (seconds * sampleRate).toInt()
        if (frames <= 0) {
            jumpTo(gain)
            return
        }
        val from = if (value < FLOOR) FLOOR else value
        val to = if (gain < FLOOR) FLOOR else gain.toDouble()
        if (from == to) {
            jumpTo(gain)
            return
        }
        target = gain
        value = from
        factor = exp(ln(to / from) / frames)
        framesRemaining = frames
        arrivedFlag = false
    }

    /** Advances one frame and returns the gain for it. */
    fun advance(): Float {
        if (framesRemaining > 0) {
            value *= factor
            framesRemaining--
            if (framesRemaining == 0) {
                value = target.toDouble()
                arrivedFlag = true
            }
        }
        return value.toFloat()
    }

    /** True once per completed ramp, for the caller that needs the edge. */
    fun consumeArrival(): Boolean {
        val arrived = arrivedFlag
        arrivedFlag = false
        return arrived
    }

    private companion object {
        val FLOOR: Double = Levels.dbToAmplitude(Levels.FADE_FLOOR_DB).toDouble()
    }
}
