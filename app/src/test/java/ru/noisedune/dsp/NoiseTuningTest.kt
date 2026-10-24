package ru.noisedune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The premise the developer tuning panel rests on.
 *
 * Two separate claims, both worth a test. First, that [NoiseTuning.FACTORY] is
 * the shipped sound exactly: the panel's whole design turns measured constants
 * into default arguments, and a typo in one of those defaults would change what
 * ships while every spectral test went on passing, because they all read the
 * same wrong default. Second, that retuning a filter in place converges on the
 * same sound as building it tuned — the assumption that lets a slider move a
 * parameter with no crossfade at all.
 */
class NoiseTuningTest {

    @Test
    fun `factory defaults are the shipped constants`() {
        // Spelled out as literals rather than read from the Params classes:
        // the point is to compare against the numbers `docs/decisions.md`
        // records, so taking them from the code under test would assert
        // nothing at all.
        val pink = NoiseTuning.FACTORY.pink
        assertEquals(16, pink.rows)
        assertEquals(8.0, pink.dcBlockHz, 0.0)

        val brown = NoiseTuning.FACTORY.brown
        assertEquals(30.0, brown.integratorHz, 0.0)
        assertEquals(30.0, brown.highPassHz, 0.0)
        assertEquals(0.707, brown.highPassQ, 0.0)

        val shush = NoiseTuning.FACTORY.shush
        assertEquals(1980.0, shush.hpHz, 0.0)
        assertEquals(2500.0, shush.f1Hz, 0.0)
        assertEquals(9.55, shush.f1Q, 0.0)
        assertEquals(6575.0, shush.f2Hz, 0.0)
        assertEquals(1.95, shush.f2Q, 0.0)
        assertEquals(0.06f, shush.upperFormantGain, 0.0f)
        assertEquals(6900.0, shush.lpHz, 0.0)
        assertEquals(3.0, shush.lpQ, 0.0)
    }

    @Test
    fun `factory derives the row scale from the row count`() {
        assertEquals(1f / 17f, NoiseTuning.FACTORY.pink.scale, 0.0f)
    }

    @Test
    fun `every voice starts with no trim`() {
        for (type in NoiseType.entries) {
            assertEquals("$type trim", 0f, NoiseTuning.FACTORY.trimDb(type), 0.0f)
            assertEquals("$type trim gain", 1f, NoiseTuning.FACTORY.trimAmplitude(type), 1e-6f)
        }
    }

    @Test
    fun `a trim is decibels on top of the measured calibration`() {
        val tuning = NoiseTuning.FACTORY.withTrimDb(NoiseType.PINK, 6.0f)
        assertEquals(6.0f, tuning.trimDb(NoiseType.PINK), 0.0f)
        // +6 dB is a factor of two, near enough.
        assertEquals(2.0f, tuning.trimAmplitude(NoiseType.PINK), 0.01f)
        // And it leaves the other voices alone.
        assertEquals(0f, tuning.trimDb(NoiseType.BROWN), 0.0f)
    }

    @Test
    fun `only pink's row count needs a rebuild`() {
        val rows = NoiseTuning.FACTORY.copy(
            pink = NoiseTuning.FACTORY.pink.copy(rows = 12),
        )
        assertTrue(rows.requiresRebuild(NoiseType.PINK, NoiseTuning.FACTORY))

        // A coefficient change does not, which is what keeps a dragged slider
        // out of the crossfade machinery.
        val corner = NoiseTuning.FACTORY.copy(
            pink = NoiseTuning.FACTORY.pink.copy(dcBlockHz = 20.0),
        )
        assertTrue(!corner.requiresRebuild(NoiseType.PINK, NoiseTuning.FACTORY))

        val formant = NoiseTuning.FACTORY.copy(
            shush = NoiseTuning.FACTORY.shush.copy(f1Hz = 3500.0),
        )
        assertTrue(!formant.requiresRebuild(NoiseType.SHUSH, NoiseTuning.FACTORY))
    }

    @Test
    fun `retuning a running generator converges on the tuned spectrum`() {
        val tuned = ShushNoise.Params(f1Hz = 3600.0, f1Q = 4.0, lpHz = 6000.0, lpQ = 0.707)

        val built = ShushNoise(Spectrum.SAMPLE_RATE, SEED, tuned)

        // The interesting case: a generator that has been running at factory
        // settings, so its filters carry real state, and is then moved. If
        // retuning were done by resetting the filters this would still pass;
        // what it pins down is that keeping the state does not leave the voice
        // somewhere other than where it was aimed.
        val retuned = ShushNoise(Spectrum.SAMPLE_RATE, SEED)
        val scratch = FloatArray(SETTLE_FRAMES)
        retuned.render(scratch, SETTLE_FRAMES)
        retuned.retune(tuned)
        retuned.render(scratch, SETTLE_FRAMES)

        val builtPsd = Spectrum.psd(built)
        val retunedPsd = Spectrum.psd(retuned)

        for ((low, high) in BANDS) {
            val a = Spectrum.bandPowerDb(builtPsd, low, high)
            val b = Spectrum.bandPowerDb(retunedPsd, low, high)
            // The two have consumed different stretches of the noise stream, so
            // they are different realisations of the same process rather than
            // the same samples; 1 dB is the Welch averaging's own spread.
            assertEquals("band $low-$high Hz", a, b, 1.0)
        }

        assertEquals(
            "peak frequency",
            Spectrum.peakFrequencyHz(builtPsd),
            Spectrum.peakFrequencyHz(retunedPsd),
            200.0,
        )
    }

    @Test
    fun `retuning actually moves the spectrum`() {
        // Guards the test above: if `retune` quietly did nothing, every band
        // would still match, because both sides would be at factory.
        val factory = Spectrum.psd(ShushNoise(Spectrum.SAMPLE_RATE, SEED))
        val moved = Spectrum.psd(
            ShushNoise(Spectrum.SAMPLE_RATE, SEED).apply {
                retune(ShushNoise.Params(f1Hz = 3600.0, f1Q = 4.0, lpHz = 6000.0, lpQ = 0.707))
            },
        )
        // The peak rather than a band level: factory peaks at 2.5 kHz, the
        // target at 3.6 kHz, so a retune that did nothing cannot pass by luck.
        val shift = abs(Spectrum.peakFrequencyHz(factory) - Spectrum.peakFrequencyHz(moved))
        assertTrue("moving the main formant should move the peak (moved $shift Hz)", shift > 500.0)
    }

    @Test
    fun `brown and pink retune without going unstable`() {
        // A Q or a corner arriving from a slider must not be able to make a
        // filter diverge, which is the one failure mode that would be heard as
        // a full-scale blast rather than as a wrong tone.
        val pink = PinkNoise(Spectrum.SAMPLE_RATE, SEED)
        pink.retune(PinkNoise.Params(dcBlockHz = 60.0))
        val brown = BrownNoise(Spectrum.SAMPLE_RATE, SEED)
        brown.retune(BrownNoise.Params(integratorHz = 200.0, highPassHz = 10.0, highPassQ = 3.0))

        for (source in listOf(pink, brown)) {
            val stats = TimeStats(source, SAMPLE_COUNT)
            assertTrue("$source produced a non-finite RMS", stats.rms.isFinite())
            assertTrue("$source peaked at ${stats.peak}", stats.peak < 2.0)
        }
    }

    @Test
    fun `the snippet says so when nothing has been changed`() {
        val snippet = NoiseTuning.FACTORY.toKotlinSnippet(48_000, "test")
        assertTrue(snippet.contains("shipped sound verbatim"))
        assertTrue("should not list unchanged values", !snippet.contains("// was"))
    }

    @Test
    fun `the snippet shows what each changed value was`() {
        val snippet = NoiseTuning.FACTORY
            .copy(shush = NoiseTuning.FACTORY.shush.copy(f1Hz = 3600.0))
            .withTrimDb(NoiseType.PINK, 1.5f)
            .toKotlinSnippet(48_000, "test")

        assertTrue("the new value", snippet.contains("val f1Hz: Double = 3600.0,"))
        assertTrue("the old value", snippet.contains("// was 2500.0"))
        // Untouched parameters appear without an annotation, so the block can
        // be pasted whole.
        assertTrue("an untouched value", snippet.contains("val f2Hz: Double = 6575.0,"))
        // The trim is folded into the enum's absolute gain, with the sum shown.
        assertTrue("the trim arithmetic", snippet.contains("x +1.5 dB"))
        assertTrue("the device and rate", snippet.contains("test at 48000 Hz"))
    }

    private companion object {
        const val SEED = 20_260_207L
        const val SAMPLE_COUNT = 1_000_000
        const val SETTLE_FRAMES = 48_000

        /** Wide bands: this is a spectrum comparison, not a bin comparison. */
        val BANDS = listOf(
            500.0 to 1_000.0,
            1_000.0 to 2_000.0,
            2_000.0 to 4_000.0,
            4_000.0 to 8_000.0,
            8_000.0 to 16_000.0,
        )
    }
}
