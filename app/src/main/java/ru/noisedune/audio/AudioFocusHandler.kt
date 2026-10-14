package ru.noisedune.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * Keeps the noise polite: out of the way of a phone call, quieter under a
 * navigation prompt, gone when another app takes over playback for good.
 */
class AudioFocusHandler(
    context: Context,
    private val onDuck: (Boolean) -> Unit,
    private val onPauseForFocus: () -> Unit,
    private val onResumeAfterFocus: () -> Unit,
    private val onStopForFocus: () -> Unit,
) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var pausedForTransientLoss = false

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        // We duck ourselves, smoothly, rather than letting the framework pause us.
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    pausedForTransientLoss = false
                    onStopForFocus()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    pausedForTransientLoss = true
                    onPauseForFocus()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onDuck(true)

                AudioManager.AUDIOFOCUS_GAIN -> {
                    onDuck(false)
                    if (pausedForTransientLoss) {
                        pausedForTransientLoss = false
                        onResumeAfterFocus()
                    }
                }
            }
        }
        .build()

    fun request(): Boolean =
        audioManager?.requestAudioFocus(focusRequest) ==
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED

    fun abandon() {
        pausedForTransientLoss = false
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }
}
