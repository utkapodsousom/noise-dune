package ru.noisedune.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.noisedune.R
import ru.noisedune.playback.NoiseSettings
import ru.noisedune.playback.PlaybackStatus
import java.util.Date
import kotlin.math.roundToInt

/**
 * The small line at the bottom of the noise field, and the way into settings.
 *
 * It answers the two questions a half-asleep parent actually has and nothing
 * else: how loud is it, and when does it stop. The level is here because it is
 * the one number the screen does not show anywhere else — the volume control is
 * a bare track — and because knowing that 45% worked last night is how you get
 * back to it tonight. The stop time is a clock time rather than the countdown
 * already running under the orb: "still on when I come back?" is a different
 * question from "how much is left", and the answer to it is a time of day.
 *
 * It used to read the spectral slope as well, −3 dB/oct and so on. That was a
 * DSP measurement restating a choice the highlighted voice pill already shows,
 * in a language that helps nobody at three in the morning.
 *
 * Tapping it opens the sheet, which is why the entry point lives here: touch
 * the thing you want to change. It also puts it at the bottom centre, the one
 * region a thumb reaches without regripping, from either hand. A gear in a top
 * corner sits some 855 dp up a 914 dp screen — outside the roughly 300 dp arc a
 * thumb sweeps — and whichever corner it took would be the far one for
 * somebody.
 *
 * Small but not ornamental, so the line sits at the 12 sp floor rather than the
 * 10 sp one used for micro-labels, and is laid directly on the field with no
 * plate behind it. The size of the target is a different question from the size
 * of the type, and is 48 dp.
 */
@Composable
fun NoiseMeta(
    settings: NoiseSettings,
    status: PlaybackStatus,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val colors = MaterialTheme.colorScheme
    val level = stringResource(R.string.meta_volume, (settings.volume * 100).roundToInt())

    // Counted from what is actually left rather than from the preset: the timer
    // may have been extended with "+15 min", or simply be half spent.
    val stopping = if (status.timerRemainingMs > 0) {
        stringResource(R.string.meta_stops_at, formatClockTime(status.timerRemainingMs))
    } else {
        // Nothing for "no timer": that is already legible from the empty line
        // under the orb, and saying so here would be the third place the same
        // fact appears.
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(
                // Announced as an action without replacing the line's own text,
                // so a screen reader still reads out what it summarises.
                onClickLabel = stringResource(R.string.action_settings),
                role = Role.Button,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onOpenSettings()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // A chevron rather than a sheet-style drag handle. A handle promises a
        // swipe, and a swipe up from the bottom edge is the system Home gesture
        // — not something an app can reliably take. This promises a tap, which
        // is what it does.
        Icon(
            painter = painterResource(R.drawable.ic_chevron_up),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text(
            text = listOfNotNull(level, stopping).joinToString(SEPARATOR).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * The wall-clock time [millisFromNow] in the future, in whatever shape the
 * system is set to — 24 hour or 12 — rather than a format hard-coded here.
 */
@Composable
internal fun formatClockTime(millisFromNow: Long): String {
    val context = LocalContext.current
    return DateFormat.getTimeFormat(context)
        .format(Date(System.currentTimeMillis() + millisFromNow))
}

private const val SEPARATOR = " · "

/** Breathing room between the line and the bottom edge of the screen. */
val MetaBottomPadding = 4.dp
