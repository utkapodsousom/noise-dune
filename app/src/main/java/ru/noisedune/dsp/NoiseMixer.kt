package ru.noisedune.dsp

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The signal graph: one generator, four gain stages, one safety clip.
 *
 * ```
 * source -> type calibration x trim -> level x fade x crossfade -> soft clip
 * ```
 *
 * Everything here runs on the audio thread except the command methods, which
 * are called from the UI or the service. Commands travel through a queue rather
 * than through shared mutable fields so that no setting can be lost and two
 * settings cannot be applied half-and-half; they are drained once per block.
 * The queue allocates one node per user action — a tap, not a sample — and only
 * ever on the calling thread.
 *
 * [prepare], [setType] and [setVoice] must all be called from one thread. They
 * share the `applied` fields below, which is how a parameter change decides
 * between rebuilding the generator and retuning it. [setLevel], [setDucked] and
 * the fades touch none of that and stay callable from anywhere, which matters
 * because [setDucked] arrives from the audio focus listener.
 */
class NoiseMixer(private val sampleRate: Int) {

    private val commands = ConcurrentLinkedQueue<Command>()
    private val fadeOutComplete = AtomicBoolean(false)

    private val levelRamp = GainRamp(sampleRate)
    private val fadeRamp = GainRamp(sampleRate)
    private val crossRamp = GainRamp(sampleRate, initial = 1f)

    /**
     * The by-ear trim on top of [NoiseType.calibration].
     *
     * A ramp rather than a plain multiplier because a step change in a gain is
     * a click. It jumps — for free — when a crossfade has already taken the
     * output to zero, and ramps when the trim is the only thing moving.
     */
    private val trimRamp = GainRamp(sampleRate, initial = 1f)

    private var activeType: NoiseType = NoiseType.PINK
    private var source: NoiseSource = activeType.createSource(sampleRate)
    private var pending: Pending? = null

    private var level: Float = 0f
    private var ducked: Boolean = false

    private var scratch = FloatArray(0)

    // ------------------------------------------- calling-thread state

    /**
     * What the audio thread has already been told. Touched only by [prepare],
     * [setType] and [setVoice], never read during [render].
     */
    private var appliedType: NoiseType = NoiseType.PINK
    private var appliedTuning: NoiseTuning = NoiseTuning.FACTORY

    // ------------------------------------------------------------- commands

    /**
     * Sets up a fresh playback. Must be called before the audio thread starts,
     * because it jumps gains instead of ramping them.
     */
    fun prepare(
        type: NoiseType,
        amplitude: Float,
        fadeInSeconds: Float,
        tuning: NoiseTuning = NoiseTuning.FACTORY,
    ) {
        commands.clear()
        fadeOutComplete.set(false)
        activeType = type
        source = tuning.build(type, sampleRate)
        pending = null
        appliedType = type
        appliedTuning = tuning
        level = amplitude
        ducked = false
        levelRamp.jumpTo(amplitude)
        crossRamp.jumpTo(1f)
        trimRamp.jumpTo(tuning.trimAmplitude(type))
        if (fadeInSeconds > 0f) {
            fadeRamp.jumpTo(0f)
            fadeRamp.rampTo(1f, fadeInSeconds)
        } else {
            fadeRamp.jumpTo(1f)
        }
    }

    fun setType(type: NoiseType) = setVoice(type, appliedTuning)

    /**
     * The voice and its parameters together.
     *
     * Rebuilds the generator only when it has to: a different voice, or a
     * structural parameter change (see [NoiseTuning.requiresRebuild]). A change
     * that is only filter coefficients retunes the live generator in place,
     * keeping its state — no allocation, no crossfade, nothing to hear. That is
     * what makes a dragged slider usable: at sixty events a second, a rebuild
     * and a 150 ms crossfade each time would pin the output near silence for
     * the whole drag.
     *
     * The generator is constructed here, on the calling thread, and shipped
     * ready-made inside the command. The audio thread must not allocate — a GC
     * pause on it is an audible gap at 3 a.m.
     */
    fun setVoice(type: NoiseType, tuning: NoiseTuning) {
        val voiceChanged = type != appliedType
        val structural = voiceChanged || tuning.requiresRebuild(type, appliedTuning)
        when {
            structural -> commands.add(
                Command.SwapSource(
                    type = type,
                    source = tuning.build(type, sampleRate),
                    trimAmplitude = tuning.trimAmplitude(type),
                    // A voice change swaps the timbre and wants the full fade;
                    // a parameter rebuild swaps a generator for a nearly
                    // identical one and only has a state discontinuity to hide.
                    crossfadeSeconds =
                        if (voiceChanged) CROSSFADE_SECONDS else PARAM_CROSSFADE_SECONDS,
                ),
            )

            tuning.paramsFor(type) != appliedTuning.paramsFor(type) ->
                commands.add(Command.Retune(tuning))
        }
        // A swap carries the trim with it, so it can jump at the bottom of the
        // crossfade. On its own the trim has to ramp.
        if (!structural && tuning.trimDb(type) != appliedTuning.trimDb(type)) {
            commands.add(Command.SetTrim(tuning.trimAmplitude(type)))
        }
        appliedType = type
        appliedTuning = tuning
    }

    fun setLevel(amplitude: Float) = commands.add(Command.SetLevel(amplitude))

    fun setDucked(value: Boolean) = commands.add(Command.SetDucked(value))

    /** Starts a fade to silence; [consumeFadeOutComplete] reports when it lands. */
    fun fadeOut(seconds: Float) = commands.add(Command.Fade(0f, seconds))

    /** Brings the level back after a fade-out was cancelled, e.g. "+15 min". */
    fun fadeIn(seconds: Float) = commands.add(Command.Fade(1f, seconds))

    /** True once, on the block where a fade-out reached silence. */
    fun consumeFadeOutComplete(): Boolean = fadeOutComplete.getAndSet(false)

    val currentType: NoiseType get() = pending?.type ?: activeType

    // -------------------------------------------------------------- render

    fun render(out: FloatArray, frames: Int) {
        drainCommands()
        if (scratch.size < frames) scratch = FloatArray(frames)

        var done = 0
        while (done < frames) {
            swapSourceIfCrossfadeLanded()

            // Never render past the moment the crossfade hits zero: that is
            // where the old generator has to give way to the new one.
            val remaining = frames - done
            val segment =
                if (pending != null) minOf(remaining, crossRamp.framesRemaining)
                else remaining

            source.render(scratch, segment)
            val calibration = activeType.calibration
            for (i in 0 until segment) {
                val gain = levelRamp.advance() * fadeRamp.advance() *
                    crossRamp.advance() * trimRamp.advance()
                out[done + i] = softClip(scratch[i] * calibration * gain)
            }
            done += segment
        }

        if (fadeRamp.consumeArrival() && fadeRamp.isAtZero) {
            fadeOutComplete.set(true)
        }
    }

    private fun swapSourceIfCrossfadeLanded() {
        val next = pending ?: return
        if (crossRamp.framesRemaining > 0) return
        // No allocation: the generator was built on the calling thread.
        source = next.source
        activeType = next.type
        // Free here, where the output is already at zero.
        trimRamp.jumpTo(next.trimAmplitude)
        pending = null
        // The swap's own duration, not the constant: a 20 ms parameter rebuild
        // must not fade back in over 150 ms.
        crossRamp.rampTo(1f, next.crossfadeSeconds)
    }

    private fun drainCommands() {
        while (true) {
            when (val command = commands.poll() ?: return) {
                is Command.SwapSource -> {
                    pending = Pending(
                        type = command.type,
                        source = command.source,
                        trimAmplitude = command.trimAmplitude,
                        crossfadeSeconds = command.crossfadeSeconds,
                    )
                    crossRamp.rampTo(0f, command.crossfadeSeconds)
                }

                is Command.Retune -> {
                    command.tuning.retune(source)
                    // A generator waiting on a crossfade has to be retuned as
                    // well, or it would land carrying the parameters it was
                    // built with and quietly undo this change. Reachable by
                    // moving pink's row count and then another pink parameter
                    // inside the same crossfade.
                    pending?.let { command.tuning.retune(it.source) }
                }

                is Command.SetTrim -> trimRamp.rampTo(command.amplitude, LEVEL_RAMP_SECONDS)

                is Command.SetLevel -> {
                    level = command.amplitude
                    applyLevel()
                }

                is Command.SetDucked -> {
                    ducked = command.value
                    applyLevel()
                }

                is Command.Fade -> fadeRamp.rampTo(command.target, command.seconds)
            }
        }
    }

    private fun applyLevel() {
        val effective = if (ducked) level * DUCK_FACTOR else level
        levelRamp.rampTo(effective, LEVEL_RAMP_SECONDS)
    }

    /**
     * A generator waiting for the crossfade to reach zero.
     *
     * [type] is the type the [source] was built for, which for a parameter
     * rebuild is the type already playing — that is what keeps [currentType]
     * honest while a rebuild is in flight.
     */
    private class Pending(
        val type: NoiseType,
        val source: NoiseSource,
        val trimAmplitude: Float,
        val crossfadeSeconds: Float,
    )

    private sealed interface Command {
        /** Carries a generator that is already built; see [setVoice]. */
        data class SwapSource(
            val type: NoiseType,
            val source: NoiseSource,
            val trimAmplitude: Float,
            val crossfadeSeconds: Float,
        ) : Command

        data class Retune(val tuning: NoiseTuning) : Command
        data class SetTrim(val amplitude: Float) : Command
        data class SetLevel(val amplitude: Float) : Command
        data class SetDucked(val value: Boolean) : Command
        data class Fade(val target: Float, val seconds: Float) : Command
    }

    private companion object {
        /** Long enough to be smooth, short enough that a tap feels immediate. */
        const val CROSSFADE_SECONDS = 0.15f

        /**
         * Parameter rebuilds get a much shorter crossfade than voice changes.
         *
         * There is no timbre to hand over, only the discontinuity in a fresh
         * generator's filter state. 20 ms — 960 frames at 48 kHz — sits below
         * the 50 ms that already governs the level ramp, and above the 10 ms or
         * so at which the dip stops hiding anything and becomes a tick itself.
         */
        const val PARAM_CROSSFADE_SECONDS = 0.02f

        /** Slider smoothing; below about 20 ms a fast drag crackles. */
        const val LEVEL_RAMP_SECONDS = 0.05f

        /** -12 dB while another app wants to be heard over us. */
        const val DUCK_FACTOR = 0.25f
    }
}
