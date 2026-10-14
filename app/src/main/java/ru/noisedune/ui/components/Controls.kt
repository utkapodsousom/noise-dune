package ru.noisedune.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.noisedune.R

/**
 * Hearing-safety note, shown once until acknowledged.
 *
 * Worth the screen space: the 2014 *Pediatrics* study that measured fourteen
 * infant sleep machines found every one of them above 50 dBA at 30 cm, so the
 * default behaviour of a sound machine is to be too loud. The wording avoids
 * implying the app knows the real level — it cannot; it sees neither the speaker
 * nor the distance.
 *
 * The 30 dBA in the copy is the Russian sanitary limit for a bedroom at night,
 * attributed by document number in `NOTICE.md` rather than here: a regulation
 * number in a card read at 3 a.m. buys nothing a parent can act on, while
 * "a whisper, not a conversation" can be checked by ear with no meter. The
 * same table's 45 dBA maximum is left out because this signal is continuous
 * broadband noise, for which the equivalent and maximum levels coincide —
 * the maximum governs transients, and there are none.
 */
@Composable
fun SafetyNotice(onAcknowledge: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.safety_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.safety_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onAcknowledge, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.safety_understood))
            }
        }
    }
}

/** The heading above a group of rows. */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

/** A labelled switch with an explanatory second line. */
@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                // The stock switch thumb is near-white when on, which is a
                // bright dot in a dark room.
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                // Deliberately not onSurfaceVariant: that is the body text
                // colour, and a thumb at text brightness is the same bright dot
                // by another route — an off switch should be the dimmest thing
                // in the row, not one of the lightest.
                uncheckedThumbColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}
