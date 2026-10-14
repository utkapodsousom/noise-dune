package ru.noisedune.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import ru.noisedune.dsp.NoiseType
import ru.noisedune.playback.NoiseSettings

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("noise_settings")

/** Loads and stores [NoiseSettings]. The runtime copy lives in `NoiseController`. */
class SettingsRepository(private val context: Context) {

    suspend fun load(): NoiseSettings {
        val prefs = context.settingsStore.data.first()
        val defaults = NoiseSettings()
        return NoiseSettings(
            type = prefs[Keys.TYPE]?.let { stored ->
                NoiseType.entries.firstOrNull { it.name == stored }
            } ?: defaults.type,
            volume = prefs[Keys.VOLUME] ?: defaults.volume,
            volumeCapped = prefs[Keys.VOLUME_CAPPED] ?: defaults.volumeCapped,
            fadeInEnabled = prefs[Keys.FADE_IN_ENABLED] ?: defaults.fadeInEnabled,
            fadeInSeconds = prefs[Keys.FADE_IN_SECONDS] ?: defaults.fadeInSeconds,
            fadeOutEnabled = prefs[Keys.FADE_OUT_ENABLED] ?: defaults.fadeOutEnabled,
            fadeOutSeconds = prefs[Keys.FADE_OUT_SECONDS] ?: defaults.fadeOutSeconds,
            timerMinutes = prefs[Keys.TIMER_MINUTES] ?: defaults.timerMinutes,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
            safetyNoticeAcknowledged =
                prefs[Keys.SAFETY_ACKNOWLEDGED] ?: defaults.safetyNoticeAcknowledged,
        )
    }

    suspend fun save(settings: NoiseSettings) {
        context.settingsStore.edit { prefs ->
            prefs[Keys.TYPE] = settings.type.name
            prefs[Keys.VOLUME] = settings.volume
            prefs[Keys.VOLUME_CAPPED] = settings.volumeCapped
            prefs[Keys.FADE_IN_ENABLED] = settings.fadeInEnabled
            prefs[Keys.FADE_IN_SECONDS] = settings.fadeInSeconds
            prefs[Keys.FADE_OUT_ENABLED] = settings.fadeOutEnabled
            prefs[Keys.FADE_OUT_SECONDS] = settings.fadeOutSeconds
            prefs[Keys.TIMER_MINUTES] = settings.timerMinutes
            prefs[Keys.KEEP_SCREEN_ON] = settings.keepScreenOn
            prefs[Keys.SAFETY_ACKNOWLEDGED] = settings.safetyNoticeAcknowledged
        }
    }

    private object Keys {
        val TYPE = stringPreferencesKey("type")
        val VOLUME = floatPreferencesKey("volume")
        val VOLUME_CAPPED = booleanPreferencesKey("volume_capped")
        val FADE_IN_ENABLED = booleanPreferencesKey("fade_in_enabled")
        val FADE_IN_SECONDS = intPreferencesKey("fade_in_seconds")
        val FADE_OUT_ENABLED = booleanPreferencesKey("fade_out_enabled")
        val FADE_OUT_SECONDS = intPreferencesKey("fade_out_seconds")
        val TIMER_MINUTES = intPreferencesKey("timer_minutes")
        // "dim_screen" used to live here. The key is deliberately not read or
        // written any more: screen dimming is gone, and a stale boolean left in
        // an existing install's file is harmless.
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SAFETY_ACKNOWLEDGED = booleanPreferencesKey("safety_acknowledged")
    }
}
