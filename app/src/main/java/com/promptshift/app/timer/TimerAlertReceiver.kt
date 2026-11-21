package com.promptshift.app.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.promptshift.app.PromptShiftApp

/**
 * Handles the 1-tap "Dismiss" action on the PromptShift Timer Done heads-up notification.
 */
class TimerAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            TimerAlertNotifier.ACTION_DISMISS_TIMER_ALERT -> {
                TimerAlertNotifier.dismissAlert(context)
                try {
                    PromptShiftApp.from(context).timerManager.dismissFinishedAlert()
                } catch (_: Exception) {
                }
            }
            TimerAlertNotifier.ACTION_CANCEL_RUNNING_TIMER -> {
                TimerAlertNotifier.cancelRunningNotification(context)
                try {
                    PromptShiftApp.from(context).timerManager.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }
}
