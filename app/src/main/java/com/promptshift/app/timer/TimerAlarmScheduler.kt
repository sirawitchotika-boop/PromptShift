package com.promptshift.app.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Schedules a Doze-safe [AlarmManager] wake-up fallback when a countdown timer starts
 * so completion alerts still fire reliably even when the Floating Bubble FGS is OFF
 * and Android pauses coroutine delays in the background.
 */
object TimerAlarmScheduler {

    private const val TAG = "PromptShiftTimerAlarm"
    private const val REQUEST_CODE_TIMER_EXPIRED = 14

    const val ACTION_TIMER_EXPIRED = "com.promptshift.app.action.TIMER_EXPIRED"
    const val EXTRA_TARGET_END_MS = "extra_target_end_ms"

    fun scheduleWakeup(context: Context, targetEndMs: Long) {
        if (targetEndMs <= 0L) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, targetEndMs)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetEndMs,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetEndMs,
                        pendingIntent,
                    )
                }
            } else {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetEndMs,
                        pendingIntent,
                    )
                } catch (_: SecurityException) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetEndMs,
                        pendingIntent,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to schedule timer Doze wake-up alarm", e)
        }
    }

    fun cancelWakeup(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            val intent = Intent(context, TimerAlertReceiver::class.java).apply {
                action = ACTION_TIMER_EXPIRED
            }
            val existing = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_TIMER_EXPIRED,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (existing != null) {
                alarmManager.cancel(existing)
                existing.cancel()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to cancel timer Doze wake-up alarm", e)
        }
    }

    private fun buildPendingIntent(context: Context, targetEndMs: Long): PendingIntent {
        val intent = Intent(context, TimerAlertReceiver::class.java).apply {
            action = ACTION_TIMER_EXPIRED
            putExtra(EXTRA_TARGET_END_MS, targetEndMs)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_TIMER_EXPIRED,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
