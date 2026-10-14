package ru.noisedune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class LevelsTest {

    @Test
    fun `the slider spans its stated decibel range`() {
        val bottom = 20.0 * log10(Levels.sliderToAmplitude(0f, capped = false).toDouble())
        val top = 20.0 * log10(Levels.sliderToAmplitude(1f, capped = false).toDouble())
        assertEquals(Levels.MIN_VOLUME_DB, bottom, 0.01)
        assertEquals(Levels.MAX_VOLUME_DB, top, 0.01)
    }

    @Test
    fun `the cap holds the top of the slider six decibels lower`() {
        val uncapped = 20.0 * log10(Levels.sliderToAmplitude(1f, capped = false).toDouble())
        val capped = 20.0 * log10(Levels.sliderToAmplitude(1f, capped = true).toDouble())
        assertEquals(-6.0, capped - uncapped, 0.01)
    }

    @Test
    fun `the slider is evenly spaced in decibels`() {
        // Equal travel should give equal change in decibels, which is what makes
        // the control feel linear to the ear.
        val quarter = 20.0 * log10(Levels.sliderToAmplitude(0.25f, capped = false).toDouble())
        val half = 20.0 * log10(Levels.sliderToAmplitude(0.5f, capped = false).toDouble())
        val threeQuarters = 20.0 * log10(Levels.sliderToAmplitude(0.75f, capped = false).toDouble())
        assertEquals(half - quarter, threeQuarters - half, 0.01)
    }

    @Test
    fun `the slider is monotonic and clamped`() {
        var previous = -1f
        for (step in 0..100) {
            val amplitude = Levels.sliderToAmplitude(step / 100f, capped = false)
            assertTrue("slider should rise monotonically", amplitude > previous)
            previous = amplitude
        }
        assertEquals(
            Levels.sliderToAmplitude(0f, capped = false),
            Levels.sliderToAmplitude(-5f, capped = false),
            0f,
        )
        assertEquals(
            Levels.sliderToAmplitude(1f, capped = false),
            Levels.sliderToAmplitude(5f, capped = false),
            0f,
        )
    }

    @Test
    fun `the soft clip leaves ordinary samples untouched`() {
        for (value in listOf(0f, 0.1f, 0.5f, 0.89f, -0.5f, -0.89f)) {
            assertEquals("should pass through unchanged", value, softClip(value), 0f)
        }
    }

    @Test
    fun `the soft clip never exceeds full scale`() {
        // The single guarantee this function exists for. Cheap tanh
        // approximations overshoot 1.0 for large inputs, which is why it uses
        // the real one.
        for (value in listOf(0.95f, 1f, 1.5f, 4f, 20f, 1000f)) {
            assertTrue("softClip($value) = ${softClip(value)}", softClip(value) <= 1f)
            assertTrue("softClip(-$value) = ${softClip(-value)}", softClip(-value) >= -1f)
        }
    }

    @Test
    fun `the soft clip is monotonic and odd`() {
        var previous = Float.NEGATIVE_INFINITY
        var value = 0f
        while (value < 3f) {
            val clipped = softClip(value)
            assertTrue("soft clip should not fold back", clipped >= previous)
            assertEquals("should be symmetric", -clipped, softClip(-value), 1e-6f)
            previous = clipped
            value += 0.001f
        }
    }
}
