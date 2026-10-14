package ru.noisedune.playback

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.noisedune.dsp.NoiseType
import ru.noisedune.service.NoiseService

/**
 * The seam between the UI and the playback service.
 *
 * A process-wide object rather than a bound service: the activity and the
 * service always live in the same process, so a shared [StateFlow] carries
 * settings down and status up with none of the binder lifecycle. The service is
 * still a real foreground service — this only replaces the plumbing, not the
 * service itself.
 *
 * Dragging the volume slider must not go through `startService` per frame, and
 * must not wait on a DataStore write, which is why the runtime state lives here
 * and persistence observes it from the side (see `NoiseDuneApp`).
 */
object NoiseController {

    private val _settings = MutableStateFlow(NoiseSettings())
    val settings: StateFlow<NoiseSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow(PlaybackStatus())
    val status: StateFlow<PlaybackStatus> = _status.asStateFlow()

    /** One-shot requests that are events, not state: "add 15 minutes". */
    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    sealed interface Command {
        /**
         * Arms (or re-arms) the sleep timer.
         *
         * An event rather than a state change so that tapping the chip that is
         * already selected restarts the countdown, which is what tapping it
         * plainly means. A [StateFlow] would swallow that tap as a no-op.
         */
        data class ArmTimer(val minutes: Int) : Command

        data class ExtendTimer(val minutes: Int) : Command
    }

    /** Replaces the defaults with what was loaded from disk. Call once, at startup. */
    fun hydrate(loaded: NoiseSettings) {
        _settings.value = loaded
    }

    fun update(transform: (NoiseSettings) -> NoiseSettings) = _settings.update(transform)

    fun setType(type: NoiseType) = update { it.copy(type = type) }

    fun setVolume(volume: Float) = update { it.copy(volume = volume.coerceIn(0f, 1f)) }

    fun setTimerMinutes(minutes: Int) {
        update { it.copy(timerMinutes = minutes) }
        _commands.tryEmit(Command.ArmTimer(minutes))
    }

    /** Called by the service only. */
    fun publishStatus(status: PlaybackStatus) {
        _status.value = status
    }

    fun extendTimer(minutes: Int = NoiseSettings.EXTEND_MINUTES) {
        _commands.tryEmit(Command.ExtendTimer(minutes))
    }

    /**
     * Starts playback.
     *
     * Must be called while the app is in the foreground — Android 12 and later
     * refuse to let a backgrounded app start a foreground service. Every caller
     * here is a user tap in the activity, which satisfies that.
     */
    fun play(context: Context) {
        context.startForegroundService(
            Intent(context, NoiseService::class.java).setAction(NoiseService.ACTION_PLAY),
        )
    }

    fun stop(context: Context) {
        context.startService(
            Intent(context, NoiseService::class.java).setAction(NoiseService.ACTION_STOP),
        )
    }
}
