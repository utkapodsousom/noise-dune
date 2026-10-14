package ru.noisedune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10

/** The fades: exact in duration, smooth enough to be inaudible, and they land. */
class GainRampTest {

    @Test
    fun `a ten second fade-in takes exactly ten seconds`() {
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(0f)
        ramp.rampTo(1f, 10f)

        val frames = 10 * SAMPLE_RATE
        repeat(frames - 1) { ramp.advance() }
        assertTrue("should not have arrived one frame early", ramp.current < 1f)

        ramp.advance()
        assertEquals("should be exactly at target", 1f, ramp.current, 0f)
        assertTrue(ramp.isSettled)
    }

    @Test
    fun `a fade is smooth enough to be inaudible`() {
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(0f)
        ramp.rampTo(1f, 10f)

        var previous = ramp.current
        var largestStepDb = 0.0
        repeat(10 * SAMPLE_RATE - 1) {
            val next = ramp.advance()
            if (previous > 0f) {
                val stepDb = abs(20.0 * log10(next / previous))
                if (stepDb > largestStepDb) largestStepDb = stepDb
            }
            previous = next
        }
        // Constant step in decibels is the whole point; a thousandth of a
        // decibel per sample cannot be heard as stepping.
        assertTrue("largest step was $largestStepDb dB", largestStepDb < 0.001)
    }

    @Test
    fun `a fade in decibels is halfway down in level at the halfway point`() {
        // The property a linear-in-amplitude fade fails: at the midpoint of a
        // 10 s fade from -60 dB, a linear ramp is only 6 dB below full, so
        // almost the whole audible rise is crammed into the final seconds.
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(0f)
        ramp.rampTo(1f, 10f)
        repeat(5 * SAMPLE_RATE) { ramp.advance() }

        val midpointDb = 20.0 * log10(ramp.current.toDouble())
        assertEquals(
            "halfway through should be halfway down in decibels",
            Levels.FADE_FLOOR_DB / 2.0,
            midpointDb,
            0.5,
        )
    }

    @Test
    fun `a fade-out reaches true silence`() {
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(1f)
        ramp.rampTo(0f, 2f)
        repeat(2 * SAMPLE_RATE) { ramp.advance() }

        assertEquals("fade-out must end at zero, not near it", 0f, ramp.current, 0f)
        assertTrue(ramp.isAtZero)
    }

    @Test
    fun `a fade is monotonic`() {
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(1f)
        ramp.rampTo(0f, 1f)
        var previous = ramp.current
        repeat(SAMPLE_RATE - 1) {
            val next = ramp.advance()
            assertTrue("level went up during a fade-out", next <= previous)
            previous = next
        }
    }

    @Test
    fun `arrival is reported exactly once`() {
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(0f)
        ramp.rampTo(1f, 1f)
        repeat(SAMPLE_RATE) { ramp.advance() }

        assertTrue("arrival should be reported", ramp.consumeArrival())
        assertTrue("arrival should not be reported twice", !ramp.consumeArrival())
    }

    @Test
    fun `a zero length ramp jumps immediately`() {
        val ramp = GainRamp(SAMPLE_RATE)
        ramp.jumpTo(0.2f)
        ramp.rampTo(0.8f, 0f)
        assertEquals(0.8f, ramp.current, 0f)
        assertTrue(ramp.isSettled)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
