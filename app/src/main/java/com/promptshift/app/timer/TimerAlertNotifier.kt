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

/**
 * Triggers the ~4-second auto-stopping pulse vibration and high-priority heads-up
 * "PromptShift Timer Done" notification with a 1-tap Dismiss action.
 */
object TimerAlertNotifier {

    const val TIMER_CHANNEL_ID = "promptshift_timer_alert_channel"
    const val TIMER_NOTIFICATION_ID = 1005
    const val ACTION_DISMISS_TIMER_ALERT = "com.promptshift.app.action.DISMISS_TIMER_ALERT"

    // 6 crisp pulses totaling 3.7 seconds (~4s), repeat = -1 (auto-stops cleanly)
    private val PULSE_TIMINGS_MS = longArrayOf(
        0L, 450L, 200L, 450L, 200L, 450L, 200L, 450L, 200L, 450L, 200L, 450L,
    )

    fun triggerTimerDoneAlert(context: Context) {
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
