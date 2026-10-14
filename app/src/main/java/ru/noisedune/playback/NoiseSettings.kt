package ru.noisedune.playback

import ru.noisedune.dsp.NoiseType

/**
 * Every persisted preference, in one immutable object.
 *
 * Keeping them together means there is one load path, one save path and one
 * thing for the service to observe, instead of ten independently racing
 * flows.
 */
data class NoiseSettings(
    val type: NoiseType = NoiseType.PINK,
    /** Slider position, 0..1; mapped to amplitude by `Levels.sliderToAmplitude`. */
    val volume: Float = 0.45f,
    val volumeCapped: Boolean = true,
    val fadeInEnabled: Boolean = true,
    val fadeInSeconds: Int = 10,
    val fadeOutEnabled: Boolean = true,
    val fadeOutSeconds: Int = 30,
    /** Sleep timer duration in minutes; 0 means no timer. */
    val timerMinutes: Int = 0,
    val keepScreenOn: Boolean = false,
    val safetyNoticeAcknowledged: Boolean = false,
) {
    val fadeInSecondsEffective: Float
        get() = if (fadeInEnabled) fadeInSeconds.toFloat() else 0f

    val fadeOutSecondsEffective: Float
        get() = if (fadeOutEnabled) fadeOutSeconds.toFloat() else 0f

    companion object {
        /** Offered in the UI as chips. */
        val TIMER_PRESETS_MINUTES = listOf(0, 15, 30, 45, 60, 90, 120)
        const val EXTEND_MINUTES = 15
    }
}

/** What the service tells the UI about what is actually happening. */
data class PlaybackStatus(
    val isPlaying: Boolean = false,
    /** True while a session exists but is held — a call came in, or the user paused. */
    val isPaused: Boolean = false,
    val type: NoiseType = NoiseType.PINK,
    /** Milliseconds until the sleep timer stops playback; 0 when no timer is armed. */
    val timerRemainingMs: Long = 0,
    /** Milliseconds of fade-in still to go; 0 once at full level. */
    val fadeInRemainingMs: Long = 0,
    val isFadingOut: Boolean = false,
)
