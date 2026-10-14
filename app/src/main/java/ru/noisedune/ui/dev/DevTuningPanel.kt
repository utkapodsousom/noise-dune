package ru.noisedune.ui.dev

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.noisedune.audio.nativeSampleRate
import ru.noisedune.dsp.NoiseTuning
import ru.noisedune.dsp.NoiseType
import ru.noisedune.dsp.toKotlinSnippet
import ru.noisedune.playback.DevTuning
import ru.noisedune.ui.components.SectionLabel
import ru.noisedune.ui.components.ToggleRow
import ru.noisedune.ui.components.VoiceTabs

/**
 * The developer tuning panel: every DSP parameter of every voice, on a slider,
 * live, while the sound is playing.
 *
 * It exists because the constants it edits were chosen by measurement — a
 * Welch periodogram and an A-weighting curve — and a measured spectrum is not
 * the same question as whether a voice is pleasant to fall asleep to, through a
 * phone speaker, across a dark room. The only instrument for that is an ear,
 * and an ear needs the parameter to move while it is listening.
 *
 * Debug builds only. Every reference to this file sits inside
 * `if (BuildConfig.DEBUG)`, so R8 removes it, `DevTuning` and
 * `DevTuningRepository` from release; `proguard-rules.pro` states that
 * assumption so the removal is a guarantee rather than a hope.
 *
 * The strings here are hardcoded English rather than resources, on purpose. The
 * labels *are* the identifiers in the source — `f1Hz`, `dcBlockHz` — and
 * translating them would make the panel harder to map onto the code it exists
 * to edit, which is the whole of its value. `res/values/strings.xml` is also
 * the shipped Russian locale, and developer strings in it would live in the
 * release resource table unless resource shrinking happened to drop them.
 * Structural absence beats a thing to verify.
 */
@Composable
fun DevTuningPanel(
    selectedType: NoiseType,
    onTypeSelected: (NoiseType) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val tuning by DevTuning.tuning.collectAsStateWithLifecycle()
    val factoryMode by DevTuning.factoryMode.collectAsStateWithLifecycle()
    val sampleRate = remember(context) { nativeSampleRate(context) }

    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(NOTICE_MS)
            notice = null
        }
    }

    // Without this, back leaves the app and takes a night of edits with it.
    BackHandler(onBack = onClose)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Tuning",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$sampleRate Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = onClose) { Text("Close") }
            }

            // The voice selector belongs in here, not behind a dismissal:
            // judging a parameter by ear means hearing the voice you are
            // editing, and having to close the panel to switch is the friction
            // that would make the whole tool go unused.
            VoiceTabs(selected = selectedType, onSelect = onTypeSelected)

            ToggleRow(
                title = "Factory values",
                description = "A/B against your edits, instantly",
                checked = factoryMode,
                onCheckedChange = { DevTuning.setFactoryMode(it) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = {
                        val snippet = tuning.toKotlinSnippet(sampleRate, deviceName())
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("noise tuning", snippet))
                        notice = "Copied"
                    },
                ) { Text("Copy Kotlin") }
                TextButton(
                    onClick = {
                        // One message per voice: each stays well inside
                        // logcat's per-entry limit, so `adb logcat -s
                        // NoiseTuning` is readable rather than truncated.
                        tuning.toKotlinSnippet(sampleRate, deviceName())
                            .split("\n\n")
                            .forEach { Log.i(LOG_TAG, it) }
                        notice = "Logged to $LOG_TAG"
                    },
                ) { Text("Log") }
                TextButton(
                    onClick = {
                        DevTuning.resetAll()
                        notice = "Reset to factory"
                    },
                ) { Text("Reset all") }
            }

            notice?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            NoiseType.entries.forEach { type ->
                VoiceSection(type = type, tuning = tuning, isPlayingVoice = type == selectedType)
            }
        }
    }
}

@Composable
private fun VoiceSection(type: NoiseType, tuning: NoiseTuning, isPlayingVoice: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(
            // Marking which section you are actually hearing, because the
            // panel shows all four and only one of them is audible.
            if (isPlayingVoice) "${type.name} — playing" else type.name,
        )

        if (type == NoiseType.BROWN) {
            Caption(
                "Corner and Q set this voice's peak headroom, which is the " +
                    "whole app's loudness ceiling. Expect to re-trim after moving them.",
            )
        }
        if (type == NoiseType.WHITE) {
            Caption("No parameters — uniform samples straight from the RNG.")
        }

        paramsFor(type).forEach { param ->
            DevSlider(param = param, tuning = tuning)
        }
        DevSlider(param = trimParam(type), tuning = tuning)
    }
}

/**
 * One parameter.
 *
 * A stock Material slider rather than the app's own `VolumeBar`: that one is
 * deliberately thumbless because it gets grabbed in the dark without looking,
 * which is the exact opposite of a precision control being read in the light.
 *
 * The factory value is shown whenever the current one is off it, so the
 * question "how far have I wandered" never needs the source open — and so the
 * number to put back is on screen.
 */
@Composable
private fun DevSlider(param: DevParam, tuning: NoiseTuning) {
    val value = param.get(tuning)
    val factory = param.get(NoiseTuning.FACTORY)
    val changed = value != factory

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                param.label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    param.readout(value),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (changed) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (changed) {
                    Text(
                        "was ${param.readout(factory)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
        Slider(
            value = param.toPosition(value),
            onValueChange = { position ->
                DevTuning.update { param.set(it, param.fromPosition(position)) }
            },
        )
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

private const val LOG_TAG = "NoiseTuning"
private const val NOTICE_MS = 2_000L
