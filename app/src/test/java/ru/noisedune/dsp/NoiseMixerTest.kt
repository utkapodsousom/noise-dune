package ru.noisedune.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The gain stages and the crossfade, where the voices meet the output. */
class NoiseMixerTest {

    private fun renderSeconds(mixer: NoiseMixer, seconds: Double): FloatArray {
        val total = (seconds * SAMPLE_RATE).toInt()
        val output = FloatArray(total)
        val chunk = FloatArray(CHUNK)
        var written = 0
        while (written < total) {
            val frames = minOf(CHUNK, total - written)
            mixer.render(chunk, frames)
            chunk.copyInto(output, written, 0, frames)
            written += frames
        }
        return output
    }

    @Test
    fun `output never leaves full scale at maximum level`() {
        for (type in NoiseType.entries) {
            val mixer = NoiseMixer(SAMPLE_RATE)
            mixer.prepare(type, amplitude = 1f, fadeInSeconds = 0f)
            val output = renderSeconds(mixer, 4.0)
            val peak = output.maxOf { abs(it) }
            assertTrue("$type peaked at $peak", peak <= 1f)
        }
    }

    @Test
    fun `output stays in range while switching type at full level`() {
        // Switching crossfades one generator out and another in; for a moment
        // both gain stages are moving at once.
        val mixer = NoiseMixer(SAMPLE_RATE)
        mixer.prepare(NoiseType.BROWN, amplitude = 1f, fadeInSeconds = 0f)
        renderSeconds(mixer, 0.5)
        for (type in listOf(NoiseType.WHITE, NoiseType.SHUSH, NoiseType.PINK, NoiseType.BROWN)) {
            mixer.setType(type)
            val output = renderSeconds(mixer, 0.5)
            val peak = output.maxOf { abs(it) }
            assertTrue("peaked at $peak while switching to $type", peak <= 1f)
        }
    }

    @Test
    fun `a crossfade dips to silence and comes back`() {
        val mixer = NoiseMixer(SAMPLE_RATE)
        mixer.prepare(NoiseType.PINK, amplitude = 1f, fadeInSeconds = 0f)
        renderSeconds(mixer, 0.2)
        mixer.setType(NoiseType.WHITE)

        val output = renderSeconds(mixer, 0.5)
        // The dip is the crossfade handing over; without it, the generator would
        // swap mid-waveform and click.
        val quietest = windowPeaks(output).min()
        val loudest = windowPeaks(output).max()
        assertTrue("crossfade should dip towards silence (min window peak $quietest)", quietest < 0.02f)
        assertTrue("and should come back up (max window peak $loudest)", loudest > 0.05f)
    }

    @Test
    fun `fade-out completion is reported exactly once`() {
        val mixer = NoiseMixer(SAMPLE_RATE)
        mixer.prepare(NoiseType.PINK, amplitude = 1f, fadeInSeconds = 0f)
        mixer.fadeOut(1f)

        renderSeconds(mixer, 0.5)
        assertTrue("should not report before the fade ends", !mixer.consumeFadeOutComplete())

        renderSeconds(mixer, 1.0)
        assertTrue("should report once the fade lands", mixer.consumeFadeOutComplete())
        assertTrue("should not report twice", !mixer.consumeFadeOutComplete())
    }

    @Test
    fun `a fade-out ends in actual silence`() {
        val mixer = NoiseMixer(SAMPLE_RATE)
        mixer.prepare(NoiseType.WHITE, amplitude = 1f, fadeInSeconds = 0f)
        mixer.fadeOut(0.5f)
        renderSeconds(mixer, 1.0)

        val tail = renderSeconds(mixer, 0.2)
        assertEquals("silence means zero, not nearly zero", 0f, tail.maxOf { abs(it) }, 0f)
    }

    @Test
    fun `a fade-in starts quiet and arrives at full level`() {
        val mixer = NoiseMixer(SAMPLE_RATE)
        mixer.prepare(NoiseType.WHITE, amplitude = 1f, fadeInSeconds = 10f)

        val start = renderSeconds(mixer, 0.5)
        val startPeak = start.maxOf { abs(it) }
        assertTrue("a fade-in should begin near silence (was $startPeak)", startPeak < 0.01f)

        renderSeconds(mixer, 10.0)
        val settled = renderSeconds(mixer, 0.5)
        assertTrue(
            "and should reach normal level (was ${settled.maxOf { abs(it) }})",
            settled.maxOf { abs(it) } > 0.02f,
        )
    }

    private fun windowPeaks(samples: FloatArray): List<Float> =
        samples.toList().chunked(WINDOW).map { window -> window.maxOf { abs(it) } }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val CHUNK = 1024
        const val WINDOW = 512
    }
}
