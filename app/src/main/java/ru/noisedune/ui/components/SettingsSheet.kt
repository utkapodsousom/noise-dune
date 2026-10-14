package ru.noisedune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.noisedune.R
import ru.noisedune.playback.NoiseSettings

/**
 * Everything that is not needed while the thing is playing.
 *
 * A sheet rather than a second screen: it can be opened and dismissed
 * one-handed with a thumb, which is the only way this app is ever held. Its
 * contents may scroll; the screen behind it never does.
 *
 * There is no Save, no Cancel and no close button. Every control here writes
 * straight through to `NoiseController`, which is observed by both the service
 * and the DataStore, so a confirmation step would be a lie about how the state
 * actually travels. Dismissal is the swipe and the scrim, as it is everywhere
 * else in the system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: NoiseSettings,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onTimerSelected: (Int) -> Unit,
    onFadeInToggled: (Boolean) -> Unit,
    onFadeOutToggled: (Boolean) -> Unit,
    onVolumeCapToggled: (Boolean) -> Unit,
    onKeepScreenOnToggled: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainer,
        contentColor = colors.onSurfaceVariant,
        scrimColor = colors.background.copy(alpha = 0.8f),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(stringResource(R.string.timer))
                TimerRow(
                    selectedMinutes = settings.timerMinutes,
                    onSelect = onTimerSelected,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column {
                SectionLabel(stringResource(R.string.settings_preferences))
                ToggleRow(
                    title = stringResource(R.string.setting_fade_in),
                    description = stringResource(
                        R.string.setting_fade_in_desc,
                        settings.fadeInSeconds,
                    ),
                    checked = settings.fadeInEnabled,
                    onCheckedChange = onFadeInToggled,
                )
                ToggleRow(
                    title = stringResource(R.string.setting_fade_out),
                    description = stringResource(
                        R.string.setting_fade_out_desc,
                        settings.fadeOutSeconds,
                    ),
                    checked = settings.fadeOutEnabled,
                    onCheckedChange = onFadeOutToggled,
                )
                ToggleRow(
                    title = stringResource(R.string.setting_volume_cap),
                    description = stringResource(R.string.setting_volume_cap_desc),
                    checked = settings.volumeCapped,
                    onCheckedChange = onVolumeCapToggled,
                )
                ToggleRow(
                    title = stringResource(R.string.setting_keep_screen_on),
                    description = stringResource(R.string.setting_keep_screen_on_desc),
                    checked = settings.keepScreenOn,
                    onCheckedChange = onKeepScreenOnToggled,
                )
            }
        }
    }
}
