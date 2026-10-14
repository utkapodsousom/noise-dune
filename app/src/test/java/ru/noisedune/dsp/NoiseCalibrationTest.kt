package ru.noisedune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

/**
 * Loudness matching between the voices.
 *
 * This is the test that protects the thing a user would notice first and blame
 * the app for: tapping a different noise at 2 a.m. and getting a jolt. Equal
 * amplitude is not equal loudness, so the check is A-weighted.
 */
class NoiseCalibrationTest {

    private fun source(type: NoiseType) =
        type.createSource(Spectrum.SAMPLE_RATE, seed = SEED)

    @Test
    fun `every voice lands on the same A-weighted level`() {
        for (type in NoiseType.entries) {
            val raw = Spectrum.aWeightedLevelDbfs(Spectrum.psd(source(type)))
            val calibrated = raw + 20.0 * log10(type.calibration.toDouble())
            assertEquals(
                "$type should sit on the calibration target",
                Levels.TARGET_A_WEIGHTED_DBFS,
                calibrated,
                0.6,
            )
        }
    }

    @Test
    fun `calibrated voices keep headroom below the soft clip`() {
        // Brown is the binding case: most of its energy is subsonic, so it needs
        // the most gain and reaches the highest peaks. If this starts failing,
        // the soft clip is being asked to do real work instead of catching the
        // odd stray sample.
        for (type in NoiseType.entries) {
            val stats = TimeStats(source(type), SAMPLE_COUNT, calibration = type.calibration)
            assertTrue(
                "$type peaked at ${stats.peak} after calibration",
                stats.peak < 0.9,
            )
        }
    }

    @Test
    fun `the same seed always produces the same stream`() {
        // Without this, every spectral test in the suite could flake.
        for (type in NoiseType.entries) {
            val first = FloatArray(BLOCK)
            val second = FloatArray(BLOCK)
            source(type).render(first, BLOCK)
            source(type).render(second, BLOCK)
            assertTrue("$type diverged for a fixed seed", first.contentEquals(second))
        }
    }

    private companion object {
        const val SEED = 20_260_207L
        const val SAMPLE_COUNT = 2_000_000
        const val BLOCK = 100_000
    }
}
