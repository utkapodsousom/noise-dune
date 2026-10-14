package ru.noisedune.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Spectrum measurement for the DSP tests.
 *
 * Small enough to live in the test sources rather than pull in a numerics
 * dependency, and the only thing these tests really need: a Welch periodogram,
 * a slope fit and A-weighting.
 */
object Spectrum {

    const val SAMPLE_RATE = 48_000
    const val BLOCK = 16_384
    const val BLOCKS = 32

    /** Mean power spectral density per bin, in linear power units. */
    fun psd(source: NoiseSource, blocks: Int = BLOCKS): DoubleArray {
        val window = DoubleArray(BLOCK) { 0.5 - 0.5 * cos(2.0 * PI * it / BLOCK) }
        val windowPower = window.sumOf { it * it }
        val normalisation = 1.0 / (windowPower * SAMPLE_RATE)

        val accumulator = DoubleArray(BLOCK / 2 + 1)
        val real = DoubleArray(BLOCK)
        val imaginary = DoubleArray(BLOCK)
        val rendered = FloatArray(BLOCK)

        repeat(blocks) {
            source.render(rendered, BLOCK)
            for (i in 0 until BLOCK) {
                real[i] = rendered[i] * window[i]
                imaginary[i] = 0.0
            }
            fft(real, imaginary)
            for (k in accumulator.indices) {
                accumulator[k] +=
                    (real[k] * real[k] + imaginary[k] * imaginary[k]) * normalisation
            }
        }
        for (k in accumulator.indices) accumulator[k] /= blocks
        return accumulator
    }

    /**
     * Least-squares slope of the spectrum in dB per octave over `[lowHz, highHz]`.
     *
     * Averaged into sixth-octave bands first, so that the fit is not dominated
     * by the thousands of FFT bins that sit in the top octave.
     */
    fun slopeDbPerOctave(psd: DoubleArray, lowHz: Double, highHz: Double): Double {
        val binHz = SAMPLE_RATE.toDouble() / BLOCK
        val bands = Math.ceil(log2(highHz / lowHz) * 6).toInt()
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        var count = 0
        for (band in 0 until bands) {
            val low = lowHz * 2.0.pow(band / 6.0)
            val high = lowHz * 2.0.pow((band + 1) / 6.0)
            val firstBin = Math.ceil(low / binHz).toInt()
            val lastBin = Math.floor(high / binHz).toInt()
            if (lastBin < firstBin || lastBin >= psd.size) continue
            var sum = 0.0
            for (k in firstBin..lastBin) sum += psd[k]
            val mean = sum / (lastBin - firstBin + 1)
            if (mean <= 0.0) continue
            val x = log2(sqrt(low * high))
            val y = 10.0 * log10(mean)
            sumX += x
            sumY += y
            sumXX += x * x
            sumXY += x * y
            count++
        }
        return (count * sumXY - sumX * sumY) / (count * sumXX - sumX * sumX)
    }

    /** A-weighted level relative to full scale, integrated over the spectrum. */
    fun aWeightedLevelDbfs(psd: DoubleArray): Double {
        val binHz = SAMPLE_RATE.toDouble() / BLOCK
        var power = 0.0
        for (k in 1 until psd.size) power += psd[k] * aWeightPowerGain(k * binHz) * binHz
        return 10.0 * log10(power)
    }

    /** Total power in a frequency band, in dB. */
    fun bandPowerDb(psd: DoubleArray, lowHz: Double, highHz: Double): Double {
        val binHz = SAMPLE_RATE.toDouble() / BLOCK
        var power = 0.0
        for (k in 1 until psd.size) {
            val frequency = k * binHz
            if (frequency >= lowHz && frequency < highHz) power += psd[k] * binHz
        }
        return 10.0 * log10(power)
    }

    /** Centre frequency of the strongest twelfth-octave band. */
    fun peakFrequencyHz(psd: DoubleArray): Double {
        val binHz = SAMPLE_RATE.toDouble() / BLOCK
        var bestFrequency = 0.0
        var bestDensity = Double.NEGATIVE_INFINITY
        for (band in 0 until 120) {
            val low = 20.0 * 2.0.pow(band / 12.0)
            val high = 20.0 * 2.0.pow((band + 1) / 12.0)
            val firstBin = maxOf(1, Math.ceil(low / binHz).toInt())
            val lastBin = minOf(psd.size - 1, Math.floor(high / binHz).toInt())
            if (lastBin < firstBin) continue
            var sum = 0.0
            for (k in firstBin..lastBin) sum += psd[k]
            val density = sum / (lastBin - firstBin + 1)
            if (density > bestDensity) {
                bestDensity = density
                bestFrequency = sqrt(low * high)
            }
        }
        return bestFrequency
    }

    /** IEC 61672 A-weighting as a power gain. */
    private fun aWeightPowerGain(frequency: Double): Double {
        if (frequency <= 0.0) return 0.0
        val f2 = frequency * frequency
        val numerator = 12194.0 * 12194.0 * f2 * f2
        val denominator = (f2 + 20.6 * 20.6) *
            sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9)) *
            (f2 + 12194.0 * 12194.0)
        val db = 20.0 * log10(numerator / denominator) + 2.00
        return 10.0.pow(db / 10.0)
    }

    /** In-place iterative radix-2 FFT. */
    private fun fft(real: DoubleArray, imaginary: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                var swap = real[i]; real[i] = real[j]; real[j] = swap
                swap = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = swap
            }
        }
        var length = 2
        while (length <= n) {
            val angle = -2.0 * PI / length
            val stepReal = cos(angle)
            val stepImaginary = sin(angle)
            var start = 0
            while (start < n) {
                var twiddleReal = 1.0
                var twiddleImaginary = 0.0
                for (k in 0 until length / 2) {
                    val a = start + k
                    val b = a + length / 2
                    val productReal = real[b] * twiddleReal - imaginary[b] * twiddleImaginary
                    val productImaginary = real[b] * twiddleImaginary + imaginary[b] * twiddleReal
                    real[b] = real[a] - productReal
                    imaginary[b] = imaginary[a] - productImaginary
                    real[a] += productReal
                    imaginary[a] += productImaginary
                    val nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
                    twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
                    twiddleReal = nextReal
                }
                start += length
            }
            length = length shl 1
        }
    }
}

/** Time-domain statistics over [sampleCount] samples. */
class TimeStats(source: NoiseSource, sampleCount: Int, calibration: Float = 1f) {
    val rms: Double
    val peak: Double
    val mean: Double

    init {
        val buffer = FloatArray(4096)
        var sum = 0.0
        var squareSum = 0.0
        var highest = 0.0
        var remaining = sampleCount
        while (remaining > 0) {
            val frames = minOf(buffer.size, remaining)
            source.render(buffer, frames)
            for (i in 0 until frames) {
                val value = buffer[i] * calibration
                sum += value
                squareSum += value.toDouble() * value
                val magnitude = if (value < 0f) -value.toDouble() else value.toDouble()
                if (magnitude > highest) highest = magnitude
            }
            remaining -= frames
        }
        rms = sqrt(squareSum / sampleCount)
        peak = highest
        mean = sum / sampleCount
    }
}
