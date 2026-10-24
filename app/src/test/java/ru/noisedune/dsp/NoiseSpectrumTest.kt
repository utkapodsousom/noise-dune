package ru.noisedune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

/**
 * The spectral contract of each voice.
 *
 * These are the tests that matter: a noise generator can be audibly wrong while
 * looking perfectly fine in code, and the only way to tell pink from
 * "nearly pink" is to measure the slope. Every expected value here was measured
 * before the implementation was written.
 */
class NoiseSpectrumTest {

    private fun source(type: NoiseType) =
        type.createSource(Spectrum.SAMPLE_RATE, seed = SEED)

    @Test
    fun `white noise is flat`() {
        val slope = Spectrum.slopeDbPerOctave(Spectrum.psd(source(NoiseType.WHITE)), 100.0, 10_000.0)
        assertEquals("white should be flat", 0.0, slope, 0.5)
    }

    @Test
    fun `pink noise falls at three decibels per octave`() {
        val slope = Spectrum.slopeDbPerOctave(Spectrum.psd(source(NoiseType.PINK)), 100.0, 10_000.0)
        assertEquals("pink should fall 3 dB per octave", -3.0, slope, 0.5)
    }

    @Test
    fun `pink noise keeps its slope in every band`() {
        // Voss-McCartney is defined in samples rather than hertz, so the slope
        // should hold right across the audible range, not only on average.
        val psd = Spectrum.psd(source(NoiseType.PINK))
        for ((low, high) in listOf(20.0 to 200.0, 200.0 to 2_000.0, 2_000.0 to 20_000.0)) {
            val slope = Spectrum.slopeDbPerOctave(psd, low, high)
            assertEquals("pink slope over $low-$high Hz", -3.0, slope, 0.6)
        }
    }

    @Test
    fun `brown noise falls at six decibels per octave`() {
        val slope = Spectrum.slopeDbPerOctave(Spectrum.psd(source(NoiseType.BROWN)), 100.0, 10_000.0)
        assertEquals("brown should fall 6 dB per octave", -6.0, slope, 0.6)
    }

    @Test
    fun `shush peaks where the fricative does`() {
        // The front cavity resonance of a postalveolar fricative sits at
        // 2.5-3.5 kHz. Drifting up towards 4-7 kHz would turn it into an [s].
        val peak = Spectrum.peakFrequencyHz(Spectrum.psd(source(NoiseType.SHUSH)))
        assertTrue("shush peak at $peak Hz should be within 2000-4500 Hz", peak in 2_000.0..4_500.0)
    }

    @Test
    fun `shush has no low frequency rumble`() {
        val psd = Spectrum.psd(source(NoiseType.SHUSH))
        val lows = Spectrum.bandPowerDb(psd, 20.0, 500.0)
        val band = Spectrum.bandPowerDb(psd, 2_000.0, 5_000.0)
        assertTrue(
            "below 500 Hz should be at least 20 dB down (was ${band - lows} dB)",
            lows <= band - 20.0,
        )
    }

    @Test
    fun `shush holds a steady level`() {
        // A slow swell of a dB or two is something to listen to rather than
        // something to fall asleep through, and on a narrow-band voice it is
        // plainly audible. One-second windows of this noise scatter by well
        // under half a decibel on their own, so a wider spread than the bound
        // below is a modulation, not chance.
        val shush = source(NoiseType.SHUSH)
        val window = FloatArray(Spectrum.SAMPLE_RATE)
        val levels = DoubleArray(LEVEL_WINDOWS) {
            shush.render(window, window.size)
            var power = 0.0
            for (sample in window) power += sample.toDouble() * sample
            10.0 * log10(power / window.size)
        }
        val spread = levels.max() - levels.min()
        assertTrue("shush level wandered by $spread dB across one-second windows", spread < 1.0)
    }

    @Test
    fun `no voice drifts away from zero`() {
        // Pink and brown both random-walk without a DC blocker, and the offset
        // is inaudible but eats peak headroom and pushes the speaker cone.
        for (type in NoiseType.entries) {
            val stats = TimeStats(source(type), SAMPLE_COUNT)
            assertEquals("$type DC offset", 0.0, stats.mean, 1e-3)
        }
    }

    @Test
    fun `no voice goes unstable`() {
        // A mistuned biquad does not sound slightly wrong, it diverges to
        // infinity within a second. Raw generators are allowed to overshoot
        // unity — shush peaks near 1.16 and is brought back by its calibration
        // gain — so the bound here is loose on purpose; the real output bound is
        // asserted in NoiseCalibrationTest.
        for (type in NoiseType.entries) {
            val stats = TimeStats(source(type), SAMPLE_COUNT)
            assertTrue("$type produced a non-finite RMS", stats.rms.isFinite())
            assertTrue("$type RMS collapsed to silence", stats.rms > 1e-4)
            assertTrue("$type peaked at ${stats.peak}, which looks like divergence", stats.peak < 2.0)
        }
    }

    private companion object {
        const val SEED = 20_260_207L
        const val SAMPLE_COUNT = 2_000_000
        const val LEVEL_WINDOWS = 30
    }
}
