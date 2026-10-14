package ru.noisedune.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Date
import ru.noisedune.BuildConfig
import ru.noisedune.R
import ru.noisedune.dsp.NoiseType
import ru.noisedune.playback.NoiseSettings
import ru.noisedune.playback.PlaybackStatus
import ru.noisedune.service.formatDuration
import ru.noisedune.service.labelRes
import ru.noisedune.ui.components.MetaBottomPadding
import ru.noisedune.ui.components.NoiseField
import ru.noisedune.ui.components.NoiseMeta
import ru.noisedune.ui.components.NoiseOrb
import ru.noisedune.ui.components.SafetyNotice
import ru.noisedune.ui.components.SettingsSheet
import ru.noisedune.ui.components.VoiceTabs
import ru.noisedune.ui.components.VolumeBar
import ru.noisedune.ui.dev.DevTuningPanel

/** Everything the screen can ask the rest of the app to do. */
class NoiseActions(
    val onPlay: () -> Unit,
    val onStop: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onExtendTimer: () -> Unit,
    val onTypeSelected: (NoiseType) -> Unit,
    val onVolumeChanged: (Float) -> Unit,
    val onTimerSelected: (Int) -> Unit,
    val onFadeInToggled: (Boolean) -> Unit,
    val onFadeOutToggled: (Boolean) -> Unit,
    val onVolumeCapToggled: (Boolean) -> Unit,
    val onKeepScreenOnToggled: (Boolean) -> Unit,
    val onSafetyAcknowledged: () -> Unit,
)

/**
 * The whole app, on one screen that never scrolls.
 *
 * Three layers, bottom to top: the drifting noise field across the lower half,
 * the content column over it, and the night-dim veil over everything. The field
 * is positioned rather than laid out, so it costs the column no height and the
 * column can keep its proportions on a short screen without anything being
 * pushed off the bottom.
 *
 * The information is split so that nothing is stated twice: the orb holds the
 * voice and the playback state, the line under it holds whatever is counting
 * down, and the metadata line at the very bottom holds the configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseScreen(
    settings: NoiseSettings,
    status: PlaybackStatus,
    actions: NoiseActions,
) {
    var settingsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    // The developer tuning panel and the gesture that reveals it. Constant
    // folded away in release, which is what takes the panel out of the APK.
    val devBuild = BuildConfig.DEBUG
    var devPanelOpen by remember { mutableStateOf(false) }
    var secretTaps by remember { mutableIntStateOf(0) }
    var firstSecretTapAt by remember { mutableLongStateOf(0L) }

    val clock = rememberWallClock()

    // A Surface, not a Box with a background: Surface is what publishes
    // LocalContentColor. Without it every Text that does not name a colour
    // explicitly falls back to the Material default of black, which on this
    // theme's black background is invisible.
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val screenHeight = maxHeight

            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(FIELD_FRACTION)
                    .align(Alignment.BottomCenter),
            ) {
                NoiseField(
                    intensity = if (status.isPlaying) 1f else IDLE_INTENSITY,
                    modifier = Modifier.fillMaxSize(),
                )
                // The field has no top edge of its own — it has to arrive out
                // of the black rather than start somewhere.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(FIELD_FEATHER)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background,
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Header(
                    isPlaying = status.isPlaying,
                    time = clock,
                    onSecretTap = if (devBuild) {
                        {
                            val now = System.currentTimeMillis()
                            if (now - firstSecretTapAt > SECRET_WINDOW_MS) {
                                firstSecretTapAt = now
                                secretTaps = 1
                            } else {
                                secretTaps++
                            }
                            if (secretTaps >= SECRET_TAPS) {
                                secretTaps = 0
                                firstSecretTapAt = 0L
                                // Confirmation you can feel: the room is dark
                                // and nothing else about this gesture is visible.
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                devPanelOpen = true
                            }
                        }
                    } else {
                        {}
                    },
                )

                // fillMaxWidth as well as weight: weight only claims height, and
                // without the width the box shrinks to the orb and parks itself
                // at the column's default Start alignment, a third of the way in.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    OrbBlock(settings, status, actions, screenHeight)
                }

                BottomStack(
                    settings = settings,
                    status = status,
                    actions = actions,
                    screenHeight = screenHeight,
                    onOpenSettings = { settingsOpen = true },
                )
            }

            // Last in the box, so it covers the screen. Inside the box rather
            // than beside it, unlike the settings sheet, which brings its own
            // window.
            if (devBuild && devPanelOpen) {
                DevTuningPanel(
                    selectedType = settings.type,
                    onTypeSelected = actions.onTypeSelected,
                    onClose = { devPanelOpen = false },
                )
            }
        }
    }

    if (settingsOpen) {
        SettingsSheet(
            settings = settings,
            sheetState = sheetState,
            onDismiss = { settingsOpen = false },
            onTimerSelected = actions.onTimerSelected,
            onFadeInToggled = actions.onFadeInToggled,
            onFadeOutToggled = actions.onFadeOutToggled,
            onVolumeCapToggled = actions.onVolumeCapToggled,
            onKeepScreenOnToggled = actions.onKeepScreenOnToggled,
        )
    }
}

/**
 * Nothing up here is a control.
 *
 * It used to read the app's own name, which told you what you already knew by
 * having opened it. The clock replaces it because that is the question this
 * screen gets asked in the dark — how far into the night is it — and answering
 * it here saves leaving the app to find out.
 *
 * The settings used to be a gear in this row, which on a 914 dp screen put the
 * only way into them about 855 dp above the bottom edge — well outside the arc
 * a thumb sweeps one-handed, and unreachable without regripping a phone held in
 * the hand that is not holding a baby. It moved to the metadata line at the
 * bottom; see [NoiseMeta].
 */
@Composable
private fun Header(
    isPlaying: Boolean,
    time: String,
    onSecretTap: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Next to a clock the dot finally has a job of its own: it separates
        // "the app is open" from "the noise is running", at a glance, without
        // reading the orb.
        PresenceDot(active = isPlaying)
        Spacer(Modifier.size(8.dp))
        Text(
            time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Seven taps here inside three seconds open the developer tuning
            // panel, in debug builds. This element because the paragraph above
            // is true — nothing up here is a control, so nothing else listens
            // to it — and because it sits some 850 dp above the bottom edge,
            // far outside the arc a thumb sweeps, where it cannot be hit while
            // reaching for anything real.
            //
            // pointerInput rather than clickable: clickable would give it a
            // ripple and a button role, announcing to TalkBack a control that
            // by design is not one.
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures { onSecretTap() }
            },
        )
    }
}

/**
 * The time of day, refreshed on the minute.
 *
 * Sleeps to the next minute boundary rather than polling on a fixed interval,
 * so it costs one recomposition an hour per sixty, not one a second.
 *
 * It keeps ticking under the night veil, which is the one place in this screen
 * where something is allowed to. The veil shows this clock, so freezing it
 * would mean waking up to a stale time — and at a frame a minute it is three
 * orders of magnitude below what the drifting field costs, and sixty times
 * below the countdown it replaced there.
 */
@Composable
private fun rememberWallClock(): String {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(MILLIS_PER_MINUTE - now % MILLIS_PER_MINUTE)
        }
    }
    return formatter.format(Date(now))
}

/** A six-pixel heartbeat, so the header says "running" without a word for it. */
@Composable
private fun PresenceDot(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "presence")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "presenceAlpha",
    )
    val resting by animateFloatAsState(
        targetValue = if (active) 1f else 0.3f,
        animationSpec = tween(700),
        label = "presenceResting",
    )
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(
                MaterialTheme.colorScheme.primary
                    .copy(alpha = (if (active) alpha else 0.25f) * resting),
            ),
    )
}

@Composable
private fun OrbBlock(
    settings: NoiseSettings,
    status: PlaybackStatus,
    actions: NoiseActions,
    screenHeight: Dp,
) {
    val alive = status.isPlaying && !status.isPaused
    // The orb is the first thing to give up height on a short screen: shrinking
    // it keeps the bottom stack on screen, which is the alternative to a
    // scrollbar.
    val diameter = minOf(192.dp, screenHeight * 0.26f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NoiseOrb(
            title = stringResource(settings.type.labelRes()),
            state = stringResource(orbStateRes(status)),
            alive = alive,
            onClick = { toggle(status, actions) },
            contentDescription = stringResource(primaryActionRes(status)),
            diameter = diameter,
        )
        Spacer(Modifier.height(4.dp))
        LiveLine(status, actions)
    }
}

/**
 * The one line that changes while you watch it, and the only per-second
 * repaint on the screen.
 *
 * Empty most of the time on purpose — a fade-in or a running timer is worth a
 * line, steady playback is not, and a label that says nothing new is a label
 * that teaches you to stop reading.
 */
@Composable
private fun LiveLine(status: PlaybackStatus, actions: NoiseActions) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val text = when {
        status.isFadingOut -> stringResource(R.string.status_fading_out)
        status.fadeInRemainingMs > 0 -> stringResource(
            R.string.status_fading_in,
            formatDuration(status.fadeInRemainingMs),
        )
        status.timerRemainingMs > 0 -> stringResource(
            R.string.status_remaining,
            formatDuration(status.timerRemainingMs),
        )
        else -> null
    } ?: return

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        if (status.timerRemainingMs > 0) {
            Spacer(Modifier.size(8.dp))
            // A real button rather than a tappable Text: this is the one thing
            // on the screen you reach for half-asleep with a baby in the other
            // arm, so it needs the 48 dp target and the button role that a bare
            // pointerInput does not give it.
            TextButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    actions.onExtendTimer()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = colors.primary),
            ) {
                Text(
                    stringResource(R.string.action_extend, NoiseSettings.EXTEND_MINUTES),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun BottomStack(
    settings: NoiseSettings,
    status: PlaybackStatus,
    actions: NoiseActions,
    screenHeight: Dp,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!settings.safetyNoticeAcknowledged) {
            SafetyNotice(
                onAcknowledge = actions.onSafetyAcknowledged,
                // A share of the screen rather than a dp figure, because what
                // has to survive underneath it is the orb, and the orb is sized
                // from the screen too.
                maxHeight = screenHeight * SAFETY_MAX_FRACTION,
            )
        }
        VoiceTabs(selected = settings.type, onSelect = actions.onTypeSelected)
        VolumeBar(value = settings.volume, onValueChange = actions.onVolumeChanged)
        NoiseMeta(
            settings = settings,
            status = status,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(bottom = MetaBottomPadding),
        )
    }
}

/** One tap does the obvious thing for whatever state playback is in. */
private fun toggle(status: PlaybackStatus, actions: NoiseActions) = when {
    status.isPaused -> actions.onResume()
    status.isPlaying -> actions.onPause()
    else -> actions.onPlay()
}

private fun orbStateRes(status: PlaybackStatus): Int = when {
    status.isPaused -> R.string.status_paused
    status.isFadingOut -> R.string.status_fading_out
    status.isPlaying -> R.string.status_playing
    else -> R.string.status_idle
}

private fun primaryActionRes(status: PlaybackStatus): Int = when {
    status.isPaused -> R.string.action_resume
    status.isPlaying -> R.string.action_pause
    else -> R.string.action_play
}

/**
 * The most of the screen the first-run safety notice may take before its text
 * starts scrolling inside it. Chosen so the orb still has a workable share at a
 * 2x font scale, where the notice would otherwise claim everything the weighted
 * middle of the column had left.
 */
private const val SAFETY_MAX_FRACTION = 0.4f

/** How much of the screen the field owns, measured from the bottom. */
private const val FIELD_FRACTION = 0.48f

/** The distance over which the field emerges from the black above it. */
private val FIELD_FEATHER = 96.dp

/** How far the field is dimmed when nothing is playing. */
private const val IDLE_INTENSITY = 0.45f

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * Seven taps, three seconds. Enough that no ordinary fidget reaches it, few
 * enough to do in the dark without counting carefully.
 */
private const val SECRET_TAPS = 7
private const val SECRET_WINDOW_MS = 3_000L
