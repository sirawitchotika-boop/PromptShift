package com.promptshift.app.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.R
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android 14+ compliant specialUse Foreground Service that keeps AI SSE streaming alive
 * in the background across Mini-Keyboard, Floating Bubble, and Main Hub — even when the
 * user closes/leaves the app or does not have the Floating Bubble running.
 */
class AiGenerationForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        val app = PromptShiftApp.from(this)
        val coordinator = app.generationCoordinator

        startForegroundCompliant(buildNotification(coordinator.generationState.value))
        acquireWakeLock()

        serviceScope.launch {
            coordinator.generationState.collect { state ->
                if (state is GenerationState.Streaming) {
                    updateNotification(state)
                } else {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            PromptShiftApp.from(this).generationCoordinator.cancelGeneration()
            stopSelf()
            return START_NOT_STICKY
        }
        val currentState = PromptShiftApp.from(this).generationCoordinator.generationState.value
        if (currentState !is GenerationState.Streaming) {
            stopSelf()
            return START_NOT_STICKY
        }
        updateNotification(currentState)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PromptShift:AiStreamingLock")
            lock.setReferenceCounted(false)
            lock.acquire(WAKE_LOCK_TIMEOUT_MS)
            wakeLock = lock
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun startForegroundCompliant(notification: Notification) {
        val foregroundType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundType)
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.ai_stream_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.ai_stream_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(state: GenerationState): Notification {
        val openHubIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            20,
            openHubIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, AiGenerationForegroundService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPending = PendingIntent.getService(
            this,
            21,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = when (state) {
            is GenerationState.Streaming ->
                "Streaming ${state.unlockedCount}/${state.targetCount} from ${state.providerName}..."
            else ->
                "Preparing AI prompt stream..."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_promptshift)
            .setContentTitle(getString(R.string.ai_stream_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openPending)
            .addAction(0, getString(R.string.timer_action_cancel), cancelPending)

        if (state is GenerationState.Streaming) {
            builder.setProgress(state.targetCount.coerceAtLeast(1), state.unlockedCount, state.unlockedCount == 0)
        }

        return builder.build()
    }

    private fun updateNotification(state: GenerationState) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.notify(NOTIFICATION_ID, buildNotification(state))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "promptshift_ai_stream_channel"
        private const val NOTIFICATION_ID = 1007
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        const val ACTION_START = "com.promptshift.app.action.START_AI_STREAM"
        const val ACTION_CANCEL = "com.promptshift.app.action.CANCEL_AI_STREAM"

        fun start(context: Context) {
            try {
                val intent = Intent(context, AiGenerationForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
            }
        }
    }
}
