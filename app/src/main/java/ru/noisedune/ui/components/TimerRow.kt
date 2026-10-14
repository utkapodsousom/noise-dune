package ru.noisedune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.noisedune.R
import ru.noisedune.playback.NoiseSettings

/**
 * Sleep timer presets.
 *
 * A scrolling row rather than a wrapping grid: one line, predictable height, and
 * no reliance on flow layout. Only four and a half of the seven fit at once, so
 * it opens scrolled to whichever is selected.
 *
 * Without that it always opened at "off" with the selection somewhere off the
 * right-hand edge, and a row where nothing is highlighted does not read as
 * "scroll to find it" — it reads as "no timer set", which is the one thing you
 * would act on by setting it again.
 */
@Composable
fun TimerRow(
    selectedMinutes: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val selectedIndex = NoiseSettings.TIMER_PRESETS_MINUTES.indexOf(selectedMinutes)
    // One preset of lead-in, so the selected chip is not flush against the edge
    // with nothing before it. The list clamps at its own end, so asking for an
    // index near the tail simply shows the tail.
    val state = rememberLazyListState((selectedIndex - 1).coerceAtLeast(0))

    LazyRow(
        modifier = modifier,
        state = state,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(NoiseSettings.TIMER_PRESETS_MINUTES) { minutes ->
            FilterChip(
                selected = minutes == selectedMinutes,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(minutes)
                },
                label = {
                    Text(
                        if (minutes == 0) {
                            stringResource(R.string.timer_off)
                        } else {
                            stringResource(R.string.timer_minutes, minutes)
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
            )
        }
    }
}
