package ru.noisedune

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import ru.noisedune.playback.NoiseController
import ru.noisedune.service.NoiseService
import ru.noisedune.ui.NoiseActions
import ru.noisedune.ui.NoiseScreen
import ru.noisedune.ui.theme.NoiseDuneTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* either way */ }

    private val actions: NoiseActions by lazy { buildActions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyWindowSettings()

        setContent {
            NoiseDuneTheme {
                val settings by NoiseController.settings.collectAsStateWithLifecycle()
                val status by NoiseController.status.collectAsStateWithLifecycle()
                NoiseScreen(settings = settings, status = status, actions = actions)
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    /**
     * Keep-screen-on is a window property, not Compose state, so it is driven
     * straight from the flow rather than through a side effect inside
     * composition.
     *
     * This used to override the window brightness as well, dropping the
     * backlight to 2% while playing. That is gone: the app no longer dims the
     * screen under any circumstance, and never touches `screenBrightness`, so
     * the system's own brightness — automatic or manual — is the only thing
     * that decides how bright the screen is.
     */
    private fun applyWindowSettings() {
        lifecycleScope.launch {
            NoiseController.settings
                .map { it.keepScreenOn }
                .distinctUntilChanged()
                .collect { keepAwake ->
                    if (keepAwake) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        // Playback works either way — the foreground service is still legal
        // without a visible notification — so a refusal is not fatal, it just
        // costs the lock-screen controls.
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun sendToService(action: String) {
        startService(Intent(this, NoiseService::class.java).setAction(action))
    }

    private fun buildActions() = NoiseActions(
        onPlay = { NoiseController.play(this) },
        onStop = { NoiseController.stop(this) },
        onPause = { sendToService(NoiseService.ACTION_PAUSE) },
        onResume = { sendToService(NoiseService.ACTION_RESUME) },
        onExtendTimer = { NoiseController.extendTimer() },
        onTypeSelected = { NoiseController.setType(it) },
        onVolumeChanged = { NoiseController.setVolume(it) },
        onTimerSelected = { NoiseController.setTimerMinutes(it) },
        onFadeInToggled = { value -> NoiseController.update { it.copy(fadeInEnabled = value) } },
        onFadeOutToggled = { value -> NoiseController.update { it.copy(fadeOutEnabled = value) } },
        onVolumeCapToggled = { value -> NoiseController.update { it.copy(volumeCapped = value) } },
        onKeepScreenOnToggled = { value ->
            NoiseController.update { it.copy(keepScreenOn = value) }
        },
        onSafetyAcknowledged = {
            NoiseController.update { it.copy(safetyNoticeAcknowledged = true) }
        },
    )
}
