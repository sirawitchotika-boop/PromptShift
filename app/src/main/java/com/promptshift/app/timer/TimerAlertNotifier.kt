package com.promptshift.app.timer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.promptshift.app.R
import com.promptshift.app.ui.MainActivity
import com.promptshift.app.ui.TimerPopupActivity

/**
 * Triggers the ~4-second auto-stopping pulse vibration, high-priority heads-up
 * "PromptShift Timer Done" notification, and silent ongoing countdown notification in the shade.
 */
object TimerAlertNotifier {

    const val TIMER_CHANNEL_ID = "promptshift_timer_alert_channel"
    const val TIMER_RUNNING_CHANNEL_ID = "promptshift_timer_running_channel_v2"
    const val TIMER_NOTIFICATION_ID = 1005
    const val TIMER_RUNNING_NOTIFICATION_ID = 1006
    const val ACTION_DISMISS_TIMER_ALERT = "com.promptshift.app.action.DISMISS_TIMER_ALERT"
    const val ACTION_CANCEL_RUNNING_TIMER = "com.promptshift.app.action.CANCEL_RUNNING_TIMER"
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    // 6 crisp pulses totaling 3.7 seconds (~4s), repeat = -1 (auto-stops cleanly)
    private val PULSE_TIMINGS_MS = longArrayOf(
        0L, 450L, 200L, 450L, 200L, 450L, 200L, 450L, 200L, 450L, 200L, 450L,
    )

    fun showRunningNotification(context: Context, targetEndMs: Long) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                TIMER_RUNNING_CHANNEL_ID,
                context.getString(R.string.timer_running_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.timer_running_channel_desc)
                enableVibration(false)
                enableLights(false)
                setShowBadge(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)

            val openIntent = Intent(context, TimerPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }
            val openPending = PendingIntent.getActivity(
                context,
                12,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val cancelIntent = Intent(context, TimerAlertReceiver::class.java).apply {
                action = ACTION_CANCEL_RUNNING_TIMER
            }
            val cancelPending = PendingIntent.getBroadcast(
                context,
                13,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val promotedExtras = android.os.Bundle().apply {
                putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            }

            val notification = NotificationCompat.Builder(context, TIMER_RUNNING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_promptshift)
                .setContentTitle(context.getString(R.string.timer_running_title))
                .setContentText(context.getString(R.string.timer_running_body))
                .setWhen(targetEndMs)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .addExtras(promotedExtras)
                .setContentIntent(openPending)
                .addAction(0, context.getString(R.string.timer_action_cancel), cancelPending)
                .build()

            manager.notify(TIMER_RUNNING_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    fun cancelRunningNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(TIMER_RUNNING_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    fun triggerTimerDoneAlert(context: Context) {
        cancelRunningNotification(context)
        vibratePulseAutoStop(context)
        postHeadsUpNotification(context)
    }

    fun dismissAlert(context: Context) {
        stopVibration(context)
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(TIMER_NOTIFICATION_ID)
        } catch (_: Exception) {
        }
    }

    fun stopVibration(context: Context) {
        try {
            resolveVibrator(context)?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun vibratePulseAutoStop(context: Context) {
        try {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(PULSE_TIMINGS_MS, -1)
            vibrator.vibrate(effect)
        } catch (_: Exception) {
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun postHeadsUpNotification(context: Context) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                TIMER_CHANNEL_ID,
                context.getString(R.string.timer_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.timer_channel_desc)
                enableVibration(false) // Handled by our explicit ~4s auto-stop waveform
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPending = PendingIntent.getActivity(
                context,
                10,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val dismissIntent = Intent(context, TimerAlertReceiver::class.java).apply {
                action = ACTION_DISMISS_TIMER_ALERT
            }
            val dismissPending = PendingIntent.getBroadcast(
                context,
                11,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notification = NotificationCompat.Builder(context, TIMER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_promptshift)
                .setContentTitle(context.getString(R.string.timer_done_title))
                .setContentText(context.getString(R.string.timer_done_body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(openPending)
                .setDeleteIntent(dismissPending)
                .addAction(0, context.getString(R.string.timer_action_dismiss), dismissPending)
                .build()

            manager.notify(TIMER_NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }
}
