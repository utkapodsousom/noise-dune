package ru.noisedune.dsp

import kotlin.random.Random

/**
 * Every by-ear-tunable number in the DSP, in one immutable object.
 *
 * Shaped after `NoiseSettings`: one object, one load path, one save path, and
 * one thing for the mixer to compare itself against. The comparison is the
 * point — `data class` equality on the nested params is what lets
 * [NoiseMixer.setVoice] answer "did anything actually change, and does it need
 * a rebuild or only new coefficients" without any bookkeeping.
 *
 * Its defaults are the measured constants, so [FACTORY] is the shipped sound
 * exactly; `NoiseTuningTest` fails if any default drifts from the value
 * `docs/decisions.md` records. In a release build nothing writes to this type —
 * only [FACTORY] is ever read — which is what keeps the whole tuning path free
 * for users who are not tuning anything.
 *
 * The trims are decibels *on top of* the measured [NoiseType.calibration]
 * rather than a replacement for it: the calibration is a measurement with a
 * test that owns it, and a by-ear adjustment is a different kind of number. 0 dB
 * is the shipped gain.
 */
data class NoiseTuning(
    val pink: PinkNoise.Params = PinkNoise.Params(),
    val brown: BrownNoise.Params = BrownNoise.Params(),
    val shush: ShushNoise.Params = ShushNoise.Params(),
    val whiteTrimDb: Float = 0f,
    val pinkTrimDb: Float = 0f,
    val brownTrimDb: Float = 0f,
    val shushTrimDb: Float = 0f,
) {

    fun trimDb(type: NoiseType): Float = when (type) {
        NoiseType.WHITE -> whiteTrimDb
        NoiseType.PINK -> pinkTrimDb
        NoiseType.BROWN -> brownTrimDb
        NoiseType.SHUSH -> shushTrimDb
    }

    fun withTrimDb(type: NoiseType, db: Float): NoiseTuning = when (type) {
        NoiseType.WHITE -> copy(whiteTrimDb = db)
        NoiseType.PINK -> copy(pinkTrimDb = db)
        NoiseType.BROWN -> copy(brownTrimDb = db)
        NoiseType.SHUSH -> copy(shushTrimDb = db)
    }

    /** The trim as a gain to multiply by; 1.0 at 0 dB. */
    fun trimAmplitude(type: NoiseType): Float =
        Levels.dbToAmplitude(trimDb(type).toDouble())

    /**
     * The parameter block for [type], or null for white, which has none.
     *
     * Deliberately untyped: the only caller compares it with `!=` to decide
     * whether a retune is needed, and `data class` equality does that correctly
     * for every voice without this having to know which one it is holding.
     */
    fun paramsFor(type: NoiseType): Any? = when (type) {
        NoiseType.WHITE -> null
        NoiseType.PINK -> pink
        NoiseType.BROWN -> brown
        NoiseType.SHUSH -> shush
    }

    /** A fresh generator for [type] at these settings. */
    fun build(type: NoiseType, sampleRate: Int, seed: Long = Random.nextLong()): NoiseSource =
        when (type) {
            NoiseType.WHITE -> WhiteNoise(seed)
            NoiseType.PINK -> PinkNoise(sampleRate, seed, pink)
            NoiseType.BROWN -> BrownNoise(sampleRate, seed, brown)
            NoiseType.SHUSH -> ShushNoise(sampleRate, seed, shush)
        }

    /**
     * Applies these parameters to a generator that is already playing, in
     * place: new filter coefficients, same filter state, no allocation.
     *
     * The dispatch lives here rather than behind an interface on the generators
     * because this is already the one type that knows every voice — [build] has
     * the same `when` — and because a generator should not have to know about
     * the aggregate that drives it.
     *
     * Safe to call from the audio thread: a handful of transcendentals, well
     * under a microsecond against a 21 ms block.
     */
    fun retune(source: NoiseSource) {
        when (source) {
            is PinkNoise -> source.retune(pink)
            is BrownNoise -> source.retune(brown)
            is ShushNoise -> source.retune(shush)
            // White has no parameters, only a trim.
            else -> Unit
        }
    }

    /**
     * True when [type] cannot be moved to [other] in place and has to be
     * rebuilt. Only pink's row count qualifies: it sizes the generator's bank,
     * sets the gain derived from it and seeds the running sum.
     */
    fun requiresRebuild(type: NoiseType, other: NoiseTuning): Boolean =
        type == NoiseType.PINK && pink.rows != other.pink.rows

    companion object {
        /** The shipped sound. A singleton, so the release path allocates nothing. */
        val FACTORY = NoiseTuning()
    }
}
