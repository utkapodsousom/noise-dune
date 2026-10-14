package ru.noisedune.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.noisedune.dsp.NoiseTuning
import ru.noisedune.dsp.NoiseType

/**
 * The developer tuning panel's runtime state.
 *
 * Deliberately separate from [NoiseController]: nothing here is a user
 * preference, nothing here belongs in `noise_settings`, and in a release build
 * none of it exists — every reference sits inside `if (BuildConfig.DEBUG)`, so
 * R8 removes this object, its DataStore and the panel together.
 *
 * Shaped like [NoiseController] on purpose, though: a process-wide object whose
 * flows carry edits down to the service, because that is already how this app
 * gets a value from a finger to the audio thread.
 */
object DevTuning {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _tuning = MutableStateFlow(NoiseTuning.FACTORY)

    /** What the panel shows and what gets persisted. */
    val tuning: StateFlow<NoiseTuning> = _tuning.asStateFlow()

    private val _factoryMode = MutableStateFlow(false)

    /** While true, the engine hears the shipped constants instead of the edits. */
    val factoryMode: StateFlow<Boolean> = _factoryMode.asStateFlow()

    /**
     * What the engine actually hears.
     *
     * Routing the A/B through the same flow as an ordinary edit is what makes
     * it free: flipping it emits a different tuning down the one path that
     * already knows how to apply one, so the comparison retunes in place and is
     * instant and silent. It also compares *everything* at once — every
     * parameter and all four trims — which is what "factory versus mine"
     * should mean when the question is being settled by ear.
     */
    val effective: StateFlow<NoiseTuning> =
        combine(_tuning, _factoryMode) { edited, factory ->
            if (factory) NoiseTuning.FACTORY else edited
        }.stateIn(scope, SharingStarted.Eagerly, NoiseTuning.FACTORY)

    /** Replaces the defaults with what was loaded from disk. Call once, at startup. */
    fun hydrate(loaded: NoiseTuning) {
        _tuning.value = loaded
    }

    fun update(transform: (NoiseTuning) -> NoiseTuning) = _tuning.update(transform)

    fun setTrimDb(type: NoiseType, db: Float) = update { it.withTrimDb(type, db) }

    fun setFactoryMode(value: Boolean) {
        _factoryMode.value = value
    }

    /** Back to the shipped sound, discarding the session's edits. */
    fun resetAll() {
        _tuning.value = NoiseTuning.FACTORY
    }
}
