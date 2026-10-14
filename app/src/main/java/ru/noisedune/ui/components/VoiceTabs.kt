package ru.noisedune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.noisedune.R
import ru.noisedune.dsp.NoiseType
import ru.noisedune.service.labelRes

/**
 * The four voices as a row of flat pills.
 *
 * The labels are small — this is a row you glance at, not read — but the pills
 * are 48 dp tall regardless, because the label size and the size of the thing
 * you have to hit in the dark are different questions. The spectral description
 * of each voice does not fit at this width and lives in the content
 * description instead, which is where a screen reader expects it anyway.
 */
@Composable
fun VoiceTabs(
    selected: NoiseType,
    onSelect: (NoiseType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NoiseType.entries.forEach { type ->
            VoicePill(
                type = type,
                isSelected = type == selected,
                onSelect = { onSelect(type) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VoicePill(
    type: NoiseType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val label = stringResource(type.shortLabelRes())
    val description = stringResource(type.labelRes()) + ", " + stringResource(type.descriptionRes())

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect()
                },
            )
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        // Transparent rather than the background colour: these sit on top of the
        // lit noise field, and an opaque black fill would punch a card-shaped
        // hole in it instead of letting the dunes run behind.
        color = if (isSelected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (isSelected) {
            colors.onSecondaryContainer
        } else {
            colors.onSurfaceVariant.copy(alpha = 0.6f)
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** One word, for a pill that has a quarter of the screen width to work with. */
private fun NoiseType.shortLabelRes(): Int = when (this) {
    NoiseType.WHITE -> R.string.noise_white_short
    NoiseType.PINK -> R.string.noise_pink_short
    NoiseType.BROWN -> R.string.noise_brown_short
    NoiseType.SHUSH -> R.string.noise_shush_short
}

internal fun NoiseType.descriptionRes(): Int = when (this) {
    NoiseType.WHITE -> R.string.noise_white_desc
    NoiseType.PINK -> R.string.noise_pink_desc
    NoiseType.BROWN -> R.string.noise_brown_desc
    NoiseType.SHUSH -> R.string.noise_shush_desc
}
