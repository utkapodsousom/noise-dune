package ru.noisedune.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import ru.noisedune.dsp.BrownNoise
import ru.noisedune.dsp.NoiseTuning
import ru.noisedune.dsp.PinkNoise
import ru.noisedune.dsp.ShushNoise

private val Context.devTuningStore: DataStore<Preferences> by preferencesDataStore("dev_tuning")

/**
 * Loads and stores [NoiseTuning], debug builds only.
 *
 * A separate store from `noise_settings` rather than more keys in it: a night of
 * experiments must not be able to corrupt the settings of the app actually in
 * use, and the two have nothing to do with each other. The debug build also
 * carries `applicationIdSuffix = ".dev"`, so this file lives under a different
 * application id entirely — the shipped app never sees a tuning store at all.
 *
 * Flat keys and no JSON, so this reads like [SettingsRepository] next door and
 * needs no new dependency. Hz and Q are stored as doubles because that is what
 * the generators take; rounding them through a float would make a value fail to
 * round-trip, which in a file whose whole purpose is reproducing a sound would
 * be a quiet lie.
 */
class DevTuningRepository(private val context: Context) {

    suspend fun load(): NoiseTuning {
        val prefs = context.devTuningStore.data.first()
        val defaults = NoiseTuning()
        return NoiseTuning(
            pink = PinkNoise.Params(
                rows = prefs[Keys.PINK_ROWS] ?: defaults.pink.rows,
                dcBlockHz = prefs[Keys.PINK_DC_BLOCK_HZ] ?: defaults.pink.dcBlockHz,
            ),
            brown = BrownNoise.Params(
                integratorHz = prefs[Keys.BROWN_INTEGRATOR_HZ] ?: defaults.brown.integratorHz,
                highPassHz = prefs[Keys.BROWN_HIGH_PASS_HZ] ?: defaults.brown.highPassHz,
                highPassQ = prefs[Keys.BROWN_HIGH_PASS_Q] ?: defaults.brown.highPassQ,
            ),
            shush = ShushNoise.Params(
                hpHz = prefs[Keys.SHUSH_HP_HZ] ?: defaults.shush.hpHz,
                f1Hz = prefs[Keys.SHUSH_F1_HZ] ?: defaults.shush.f1Hz,
                f1Q = prefs[Keys.SHUSH_F1_Q] ?: defaults.shush.f1Q,
                f2Hz = prefs[Keys.SHUSH_F2_HZ] ?: defaults.shush.f2Hz,
                f2Q = prefs[Keys.SHUSH_F2_Q] ?: defaults.shush.f2Q,
                upperFormantGain =
                    prefs[Keys.SHUSH_UPPER_GAIN] ?: defaults.shush.upperFormantGain,
                lpHz = prefs[Keys.SHUSH_LP_HZ] ?: defaults.shush.lpHz,
                lpQ = prefs[Keys.SHUSH_LP_Q] ?: defaults.shush.lpQ,
            ),
            whiteTrimDb = prefs[Keys.TRIM_WHITE_DB] ?: defaults.whiteTrimDb,
            pinkTrimDb = prefs[Keys.TRIM_PINK_DB] ?: defaults.pinkTrimDb,
            brownTrimDb = prefs[Keys.TRIM_BROWN_DB] ?: defaults.brownTrimDb,
            shushTrimDb = prefs[Keys.TRIM_SHUSH_DB] ?: defaults.shushTrimDb,
        )
    }

    suspend fun save(tuning: NoiseTuning) {
        context.devTuningStore.edit { prefs ->
            prefs[Keys.PINK_ROWS] = tuning.pink.rows
            prefs[Keys.PINK_DC_BLOCK_HZ] = tuning.pink.dcBlockHz
            prefs[Keys.BROWN_INTEGRATOR_HZ] = tuning.brown.integratorHz
            prefs[Keys.BROWN_HIGH_PASS_HZ] = tuning.brown.highPassHz
            prefs[Keys.BROWN_HIGH_PASS_Q] = tuning.brown.highPassQ
            prefs[Keys.SHUSH_HP_HZ] = tuning.shush.hpHz
            prefs[Keys.SHUSH_F1_HZ] = tuning.shush.f1Hz
            prefs[Keys.SHUSH_F1_Q] = tuning.shush.f1Q
            prefs[Keys.SHUSH_F2_HZ] = tuning.shush.f2Hz
            prefs[Keys.SHUSH_F2_Q] = tuning.shush.f2Q
            prefs[Keys.SHUSH_UPPER_GAIN] = tuning.shush.upperFormantGain
            prefs[Keys.SHUSH_LP_HZ] = tuning.shush.lpHz
            prefs[Keys.SHUSH_LP_Q] = tuning.shush.lpQ
            prefs[Keys.TRIM_WHITE_DB] = tuning.whiteTrimDb
            prefs[Keys.TRIM_PINK_DB] = tuning.pinkTrimDb
            prefs[Keys.TRIM_BROWN_DB] = tuning.brownTrimDb
            prefs[Keys.TRIM_SHUSH_DB] = tuning.shushTrimDb
        }
    }

    private object Keys {
        val PINK_ROWS = intPreferencesKey("pink_rows")
        val PINK_DC_BLOCK_HZ = doublePreferencesKey("pink_dc_block_hz")

        val BROWN_INTEGRATOR_HZ = doublePreferencesKey("brown_integrator_hz")
        val BROWN_HIGH_PASS_HZ = doublePreferencesKey("brown_high_pass_hz")
        val BROWN_HIGH_PASS_Q = doublePreferencesKey("brown_high_pass_q")

        val SHUSH_HP_HZ = doublePreferencesKey("shush_hp_hz")
        val SHUSH_F1_HZ = doublePreferencesKey("shush_f1_hz")
        val SHUSH_F1_Q = doublePreferencesKey("shush_f1_q")
        val SHUSH_F2_HZ = doublePreferencesKey("shush_f2_hz")
        val SHUSH_F2_Q = doublePreferencesKey("shush_f2_q")
        val SHUSH_UPPER_GAIN = floatPreferencesKey("shush_upper_gain")
        val SHUSH_LP_HZ = doublePreferencesKey("shush_lp_hz")
        val SHUSH_LP_Q = doublePreferencesKey("shush_lp_q")

        val TRIM_WHITE_DB = floatPreferencesKey("trim_white_db")
        val TRIM_PINK_DB = floatPreferencesKey("trim_pink_db")
        val TRIM_BROWN_DB = floatPreferencesKey("trim_brown_db")
        val TRIM_SHUSH_DB = floatPreferencesKey("trim_shush_db")
    }
}
