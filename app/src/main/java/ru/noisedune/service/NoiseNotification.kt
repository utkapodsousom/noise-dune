package ru.noisedune.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import ru.noisedune.MainActivity
import ru.noisedune.R
import ru.noisedune.dsp.NoiseType
import ru.noisedune.playback.NoiseSettings

/**
 * The ongoing notification that keeps the foreground service legal and gives the
 * user controls without unlocking the phone.
 *
 * A plain notification rather than a MediaSession one: this costs the lock
 * screen transport row and hardware headset buttons, which is a deliberate
 * trade — a MediaSession needs a Player implementation to drive, and none of
 * what it buys matters much for a generator with no track list.
 */
object NoiseNotification {

    const val CHANNEL_ID = "playback"
    const val ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            // LOW: visible and ongoing, but it must never make a sound or
            // vibrate. This app exists to keep a room quiet.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        type: NoiseType,
        isPaused: Boolean,
        remainingMs: Long,
        hasTimer: Boolean,
    ): Notification {
        val typeLabel = context.getString(type.labelRes())
        val text = when {
            isPaused -> context.getString(R.string.notif_paused)
            hasTimer -> context.getString(R.string.notif_remaining, formatDuration(remainingMs))
            else -> context.getString(R.string.notif_no_timer)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_noise)
            .setContentTitle(typeLabel)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent(context))

        if (isPaused) {
            builder.addAction(
                R.drawable.ic_play,
                context.getString(R.string.action_resume),
                serviceIntent(context, NoiseService.ACTION_RESUME, REQUEST_RESUME),
            )
        } else {
            builder.addAction(
                R.drawable.ic_pause,
                context.getString(R.string.action_pause),
                serviceIntent(context, NoiseService.ACTION_PAUSE, REQUEST_PAUSE),
            )
        }

        // Only offer "+15 min" when there is actually a deadline to push back.
        if (hasTimer) {
            builder.addAction(
                R.drawable.ic_timer_plus,
                context.getString(R.string.action_extend, NoiseSettings.EXTEND_MINUTES),
                serviceIntent(context, NoiseService.ACTION_EXTEND, REQUEST_EXTEND),
            )
        }

        builder.addAction(
            R.drawable.ic_stop,
            context.getString(R.string.action_stop),
            serviceIntent(context, NoiseService.ACTION_STOP, REQUEST_STOP),
        )

        return builder.build()
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, NoiseService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private const val REQUEST_CONTENT = 0
    private const val REQUEST_PAUSE = 1
    private const val REQUEST_RESUME = 2
    private const val REQUEST_EXTEND = 3
    private const val REQUEST_STOP = 4
}

/** `0:42`, `45:00`, `1:05:00` — digits only, so it needs no plural rules. */
fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

fun NoiseType.labelRes(): Int = when (this) {
    NoiseType.WHITE -> R.string.noise_white
    NoiseType.PINK -> R.string.noise_pink
    NoiseType.BROWN -> R.string.noise_brown
    NoiseType.SHUSH -> R.string.noise_shush
}
