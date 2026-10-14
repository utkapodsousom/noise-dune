package ru.noisedune

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.noisedune.data.DevTuningRepository
import ru.noisedune.data.SettingsRepository
import ru.noisedune.playback.DevTuning
import ru.noisedune.playback.NoiseController

/**
 * Wires persistence to the runtime state once, for the whole process.
 *
 * Settings live in [NoiseController] while the app runs; this class loads them
 * at startup and writes changes back from the side. Keeping the save path out of
 * the UI means dragging the volume slider never waits on disk.
 */
class NoiseDuneApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val repository = SettingsRepository(this)
        scope.launch {
            NoiseController.hydrate(repository.load())
            NoiseController.settings
                .drop(1) // the value we just hydrated with
                // collectLatest cancels the pending delay whenever a newer value
                // arrives, which debounces a slider drag down to a single write.
                .collectLatest { settings ->
                    delay(SAVE_DEBOUNCE_MS)
                    repository.save(settings)
                }
        }

        // The developer tuning panel, on the same terms: its own store, and the
        // same debounce, so dragging a parameter slider never waits on disk
        // either. Gated so that R8 takes the whole path out of release.
        if (BuildConfig.DEBUG) {
            val devRepository = DevTuningRepository(this)
            scope.launch {
                DevTuning.hydrate(devRepository.load())
                DevTuning.tuning
                    .drop(1)
                    .collectLatest { tuning ->
                        delay(SAVE_DEBOUNCE_MS)
                        devRepository.save(tuning)
                    }
            }
        }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MS = 400L
    }
}
