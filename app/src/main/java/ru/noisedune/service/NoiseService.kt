package ru.noisedune.service

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ru.noisedune.BuildConfig
import ru.noisedune.audio.AudioFocusHandler
import ru.noisedune.audio.NoiseEngine
import ru.noisedune.dsp.Levels
import ru.noisedune.dsp.NoiseTuning
import ru.noisedune.playback.DevTuning
import ru.noisedune.playback.NoiseController
import ru.noisedune.playback.NoiseSettings
import ru.noisedune.playback.PlaybackStatus

/**
 * Keeps the noise playing while the screen is off and the app is away.
 *
 * Owns the engine, the audio focus, the sleep timer and the notification. All
 * state here is touched from the main thread only — the one exception is the
 * engine's fade-out callback, which arrives on the audio thread and is hopped
 * back across immediately.
 */
class NoiseService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var engine: NoiseEngine
    private lateinit var focus: AudioFocusHandler

    private var noisyReceiver: BroadcastReceiver? = null
    private var ticker: Job? = null

    /** A session exists: either playing or held. */
    private var sessionActive = false
    private var isPaused = false

    /** [SystemClock.elapsedRealtime] of the sleep timer's end; 0 when unarmed. */
    private var timerDeadline = 0L
    private var pausedRemaining = 0L
    private var fadeOutStarted = false
    private var fadeInEndsAt = 0L

    override fun onCreate() {
        super.onCreate()
        engine = NoiseEngine(this)
        engine.onFadeOutComplete = {
            // Arrives on the audio thread. Hop to main before touching anything,
            // not least because stopping the engine joins that very thread.
            scope.launch { stopEverything() }
        }
        focus = AudioFocusHandler(
            context = this,
            onDuck = { engine.setDucked(it) },
            onPauseForFocus = { pause(abandonFocus = false) },
            onResumeAfterFocus = { resume() },
            onStopForFocus = { stopEverything() },
        )
        NoiseNotification.createChannel(this)
        observeSettings()
        observeCommands()
        observeTuning()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> startPlayback()
            ACTION_PAUSE -> pause(abandonFocus = true)
            ACTION_RESUME -> resume()
            ACTION_EXTEND -> extendTimer(NoiseSettings.EXTEND_MINUTES)
            ACTION_STOP -> stopEverything()
            else -> if (!sessionActive) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        engine.stop()
        focus.abandon()
        unregisterNoisyReceiver()
        super.onDestroy()
    }

    // ------------------------------------------------------------- observers

    /**
     * The shipped constants, unless the developer tuning panel has something to
     * say. In a release build this folds to the singleton, so the production
     * path allocates nothing and reads exactly as it did before the panel.
     */
    private fun tuning(): NoiseTuning =
        if (BuildConfig.DEBUG) DevTuning.effective.value else NoiseTuning.FACTORY

    private fun observeSettings() {
        scope.launch {
            NoiseController.settings.collect { settings ->
                engine.setVoice(settings.type, tuning())
                engine.setAmplitude(
                    Levels.sliderToAmplitude(settings.volume, settings.volumeCapped),
                )
                if (sessionActive) {
                    publishStatus()
                    refreshNotification()
                }
            }
        }
    }

    private fun observeCommands() {
        scope.launch {
            NoiseController.commands.collect { command ->
                when (command) {
                    is NoiseController.Command.ArmTimer -> armTimer(command.minutes)
                    is NoiseController.Command.ExtendTimer -> extendTimer(command.minutes)
                }
            }
        }
    }

    /**
     * Carries the developer panel's edits to the audio thread.
     *
     * A separate collector rather than combining with the settings flow: a
     * `combine` plus `collectLatest` would put this throttle in front of the
     * volume path too, and a fast volume drag must not wait on a tuning
     * debounce.
     *
     * The throttle is the same idiom as the save debounce in `NoiseDuneApp`,
     * and relies on the same property: `collectLatest` always lets the last
     * value through, so releasing a slider always leaves the sound matching it.
     * It exists because the one parameter that cannot retune in place — pink's
     * row count — rebuilds and crossfades, and must not be asked to do that
     * sixty times a second.
     */
    private fun observeTuning() {
        if (!BuildConfig.DEBUG) return
        scope.launch {
            DevTuning.effective
                .drop(1)
                .collectLatest { tuning ->
                    delay(TUNING_THROTTLE_MS)
                    engine.setVoice(NoiseController.settings.value.type, tuning)
                }
        }
    }

    // ------------------------------------------------------------- lifecycle

    private fun startPlayback() {
        val settings = NoiseController.settings.value

        // Foreground first. The system allows five seconds from
        // startForegroundService() to post a notification, then kills us.
        startForeground(NoiseNotification.ID, buildNotification(settings))

        if (sessionActive) {
            if (isPaused) resume() else refreshNotification()
            return
        }
        if (!focus.request()) {
            stopEverything()
            return
        }

        sessionActive = true
        isPaused = false
        fadeOutStarted = false
        registerNoisyReceiver()

        val fadeIn = settings.fadeInSecondsEffective
        val now = SystemClock.elapsedRealtime()
        fadeInEndsAt = if (fadeIn > 0f) now + (fadeIn * 1000f).toLong() else 0L
        timerDeadline =
            if (settings.timerMinutes > 0) now + settings.timerMinutes * MINUTE_MS else 0L

        val opened = engine.start(
            type = settings.type,
            amplitude = Levels.sliderToAmplitude(settings.volume, settings.volumeCapped),
            fadeInSeconds = fadeIn,
            tuning = tuning(),
        )
        if (!opened) {
            // Better to collapse the session than to sit there claiming to play.
            stopEverything()
            return
        }
        startTicker()
        publishStatus()
        refreshNotification()
    }

    private fun pause(abandonFocus: Boolean) {
        if (!sessionActive || isPaused) return
        isPaused = true
        if (timerDeadline > 0L) {
            pausedRemaining = (timerDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        }
        engine.stop()
        if (abandonFocus) focus.abandon()
        unregisterNoisyReceiver()
        publishStatus()
        refreshNotification()
    }

    private fun resume() {
        if (!sessionActive || !isPaused) return
        if (!focus.request()) return

        isPaused = false
        registerNoisyReceiver()
        if (pausedRemaining > 0L) {
            timerDeadline = SystemClock.elapsedRealtime() + pausedRemaining
            pausedRemaining = 0L
        }
        fadeOutStarted = false

        val settings = NoiseController.settings.value
        // A short fade on resume, not the full bedtime fade-in: the user is
        // picking up where they left off, not settling a child for the night.
        fadeInEndsAt = SystemClock.elapsedRealtime() + (RESUME_FADE_SECONDS * 1000f).toLong()
        val opened = engine.start(
            type = settings.type,
            amplitude = Levels.sliderToAmplitude(settings.volume, settings.volumeCapped),
            fadeInSeconds = RESUME_FADE_SECONDS,
            tuning = tuning(),
        )
        if (!opened) {
            stopEverything()
            return
        }
        publishStatus()
        refreshNotification()
    }

    private fun stopEverything() {
        ticker?.cancel()
        ticker = null
        sessionActive = false
        isPaused = false
        timerDeadline = 0L
        pausedRemaining = 0L
        fadeOutStarted = false
        fadeInEndsAt = 0L
        engine.stop()
        focus.abandon()
        unregisterNoisyReceiver()
        publishStatus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ----------------------------------------------------------- sleep timer

    private fun armTimer(minutes: Int) {
        if (!sessionActive) return
        cancelPendingFadeOut()
        timerDeadline =
            if (minutes > 0) SystemClock.elapsedRealtime() + minutes * MINUTE_MS else 0L
        pausedRemaining = if (isPaused && minutes > 0) minutes * MINUTE_MS else 0L
        publishStatus()
        refreshNotification()
    }

    private fun extendTimer(minutes: Int) {
        if (!sessionActive) return
        // Nothing to push back if no deadline was ever set.
        if (timerDeadline == 0L && pausedRemaining == 0L) return
        cancelPendingFadeOut()
        if (isPaused) {
            pausedRemaining += minutes * MINUTE_MS
        } else {
            timerDeadline += minutes * MINUTE_MS
        }
        publishStatus()
        refreshNotification()
    }

    /**
     * Brings the level back up if the timer's fade-out had already begun.
     *
     * Guarded by [fadeOutStarted] rather than issued unconditionally: a blanket
     * "ramp back to full" would cut short the 10 second fade-in if the user
     * touched the timer during it.
     */
    private fun cancelPendingFadeOut() {
        if (!fadeOutStarted) return
        fadeOutStarted = false
        engine.cancelFadeOut(RESUME_FADE_SECONDS)
        fadeInEndsAt = SystemClock.elapsedRealtime() + (RESUME_FADE_SECONDS * 1000f).toLong()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                tick()
                delay(TICK_MS)
            }
        }
    }

    private fun tick() {
        if (!sessionActive) return
        if (!isPaused && timerDeadline > 0L) {
            val remaining = timerDeadline - SystemClock.elapsedRealtime()
            val fadeOutSeconds = NoiseController.settings.value.fadeOutSecondsEffective
            val fadeOutMs = (fadeOutSeconds * 1000f).toLong()
            if (!fadeOutStarted && remaining <= fadeOutMs) {
                fadeOutStarted = true
                if (fadeOutMs > 0L) {
                    // The engine reports back when it reaches silence, and that
                    // is what actually stops the session.
                    engine.fadeOut(fadeOutSeconds)
                } else {
                    stopEverything()
                    return
                }
            }
        }
        publishStatus()
        refreshNotification()
    }

    // ----------------------------------------------------------- status & ui

    private fun remainingMs(): Long = when {
        !sessionActive -> 0L
        isPaused -> pausedRemaining
        timerDeadline > 0L -> (timerDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        else -> 0L
    }

    private fun publishStatus() {
        val settings = NoiseController.settings.value
        val now = SystemClock.elapsedRealtime()
        NoiseController.publishStatus(
            PlaybackStatus(
                isPlaying = sessionActive && !isPaused,
                isPaused = sessionActive && isPaused,
                type = settings.type,
                timerRemainingMs = remainingMs(),
                fadeInRemainingMs =
                    if (sessionActive && !isPaused) (fadeInEndsAt - now).coerceAtLeast(0L) else 0L,
                isFadingOut = fadeOutStarted,
            ),
        )
    }

    private fun buildNotification(settings: NoiseSettings) = NoiseNotification.build(
        context = this,
        type = settings.type,
        isPaused = isPaused,
        remainingMs = remainingMs(),
        hasTimer = timerDeadline > 0L || pausedRemaining > 0L,
    )

    private fun refreshNotification() {
        if (!sessionActive) return
        getSystemService(NotificationManager::class.java)
            ?.notify(NoiseNotification.ID, buildNotification(NoiseController.settings.value))
    }

    // ------------------------------------------------------ becoming noisy

    /**
     * Unplugging headphones must not blast the room through the phone speaker —
     * the whole point of this app is a room that stays calm.
     */
    private fun registerNoisyReceiver() {
        if (noisyReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    pause(abandonFocus = false)
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiver = receiver
    }

    private fun unregisterNoisyReceiver() {
        noisyReceiver?.let { unregisterReceiver(it) }
        noisyReceiver = null
    }

    companion object {
        const val ACTION_PLAY = "ru.noisedune.action.PLAY"
        const val ACTION_STOP = "ru.noisedune.action.STOP"
        const val ACTION_PAUSE = "ru.noisedune.action.PAUSE"
        const val ACTION_RESUME = "ru.noisedune.action.RESUME"
        const val ACTION_EXTEND = "ru.noisedune.action.EXTEND"

        private const val MINUTE_MS = 60_000L
        private const val TICK_MS = 1_000L
        private const val RESUME_FADE_SECONDS = 3f

        /**
         * Slower than the 40 ms a parameter rebuild takes to dip and come back,
         * so even the one slider that rebuilds cannot thrash; fast enough that
         * a slider still feels connected to the sound.
         */
        private const val TUNING_THROTTLE_MS = 60L
    }
}
