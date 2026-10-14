package ru.noisedune.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import ru.noisedune.dsp.NoiseMixer
import ru.noisedune.dsp.NoiseTuning
import ru.noisedune.dsp.NoiseType
import kotlin.math.max

/**
 * The device's own output rate, or 48 kHz if it will not say.
 *
 * Top-level and internal rather than private to [NoiseEngine] because the
 * developer tuning panel has to label its exported numbers with the rate they
 * were heard at, and two places probing this separately would be two places to
 * get it wrong.
 */
internal fun nativeSampleRate(context: Context): Int {
    val manager = context.getSystemService(AudioManager::class.java)
    val reported = manager
        ?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        ?.toIntOrNull()
    return reported?.takeIf { it in 8_000..192_000 } ?: 48_000
}

/**
 * Owns the [AudioTrack] and the thread that feeds it.
 *
 * Tuned for an eight hour night rather than for low latency. Nothing here cares
 * whether a tap takes 10 ms or 200 ms to be heard, but a single buffer underrun
 * at 3 a.m. is an audible click in a silent room — so the buffer is large and
 * the track asks the framework for the power saving path.
 */
class NoiseEngine(context: Context) {

    /**
     * The device's own output rate, so the framework never has to resample.
     * Resampling would burn battery all night for no audible benefit.
     */
    val sampleRate: Int = nativeSampleRate(context)

    private val mixer = NoiseMixer(sampleRate)

    private var track: AudioTrack? = null
    private var renderThread: Thread? = null

    @Volatile
    private var running = false

    /**
     * Invoked when a fade-out has reached silence.
     *
     * Called **on the audio thread**. The listener must not call [stop] inline —
     * that would join the thread from itself and deadlock. Post it elsewhere.
     */
    var onFadeOutComplete: (() -> Unit)? = null

    val isRunning: Boolean get() = running

    /**
     * @return false if the output could not be opened, so the caller can tear
     *   the session down instead of showing a playing state over silence.
     */
    fun start(
        type: NoiseType,
        amplitude: Float,
        fadeInSeconds: Float,
        tuning: NoiseTuning = NoiseTuning.FACTORY,
    ): Boolean {
        if (running) return true
        mixer.prepare(type, amplitude, fadeInSeconds, tuning)

        val audioTrack = buildTrack() ?: return false
        track = audioTrack
        running = true
        audioTrack.play()

        renderThread = Thread({ renderLoop(audioTrack) }, "noise-render").apply {
            start()
        }
        return true
    }

    fun stop() {
        if (!running) return
        running = false
        val audioTrack = track
        track = null

        // pause() first: it makes the render thread's blocking write() return at
        // once instead of sitting on a full 200 ms buffer. Without it, stop()
        // would stall its caller — which is the main thread — for a fifth of a
        // second every time the user hits stop.
        try {
            audioTrack?.pause()
        } catch (e: IllegalStateException) {
            Log.w(TAG, "AudioTrack.pause() on an uninitialised track", e)
        }
        renderThread?.join(JOIN_TIMEOUT_MS)
        renderThread = null

        if (audioTrack != null) {
            try {
                audioTrack.stop()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "AudioTrack.stop() on an uninitialised track", e)
            }
            audioTrack.release()
        }
    }

    fun setType(type: NoiseType) = mixer.setType(type)

    /** The voice together with its parameters; see [NoiseMixer.setVoice]. */
    fun setVoice(type: NoiseType, tuning: NoiseTuning) = mixer.setVoice(type, tuning)

    fun setAmplitude(amplitude: Float) = mixer.setLevel(amplitude)

    fun setDucked(ducked: Boolean) = mixer.setDucked(ducked)

    fun fadeOut(seconds: Float) = mixer.fadeOut(seconds)

    fun cancelFadeOut(seconds: Float) = mixer.fadeIn(seconds)

    // -------------------------------------------------------------- internals

    private fun renderLoop(audioTrack: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val buffer = FloatArray(CHUNK_FRAMES)
        while (running) {
            mixer.render(buffer, CHUNK_FRAMES)
            if (mixer.consumeFadeOutComplete()) {
                onFadeOutComplete?.invoke()
            }
            var offset = 0
            while (offset < CHUNK_FRAMES && running) {
                val written = audioTrack.write(
                    buffer,
                    offset,
                    CHUNK_FRAMES - offset,
                    AudioTrack.WRITE_BLOCKING,
                )
                if (written < 0) {
                    Log.w(TAG, "AudioTrack.write failed: $written")
                    running = false
                    break
                }
                offset += written
            }
        }
    }

    private fun buildTrack(): AudioTrack? {
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBytes <= 0) {
            Log.e(TAG, "getMinBufferSize returned $minBytes for ${sampleRate}Hz")
            return null
        }
        // Mono: on a phone speaker, two decorrelated channels of noise are
        // indistinguishable from one, and mono halves the generator work.
        val wantedBytes = (sampleRate * BUFFER_SECONDS).toInt() * BYTES_PER_FRAME
        val bufferBytes = max(minBytes * 4, wantedBytes)

        return runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                // A big buffer and relaxed scheduling: the opposite of what a
                // synth wants, exactly what an all-night generator wants.
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
                .build()
        }.onFailure {
            Log.e(TAG, "Could not build AudioTrack", it)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "NoiseEngine"
        const val BUFFER_SECONDS = 0.2
        const val BYTES_PER_FRAME = 4   // mono float32
        const val CHUNK_FRAMES = 1024
        const val JOIN_TIMEOUT_MS = 1000L
    }
}
