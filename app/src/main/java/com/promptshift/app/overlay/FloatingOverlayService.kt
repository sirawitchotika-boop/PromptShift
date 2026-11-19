package com.promptshift.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.R
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.clipboard.ClipboardTrampolineActivity
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android 14 compliant specialUse Foreground Service hosting the Copy-only Floating Overlay.
 */
class FloatingOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlayController: FloatingOverlayViewController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()

        val app = PromptShiftApp.from(this)
        val repository = app.repository

        startForegroundCompliant(buildNotification(repository.batchState.value))

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val controller = FloatingOverlayViewController(
            context = this,
            windowManager = windowManager,
            onLoadClicked = { ClipboardTrampolineActivity.launch(this) },
            onClearConfirmed = { repository.clearBatch() },
            onCloseClicked = { stopSelf() },
            onPreviousClicked = { repository.stepPrevious() },
            onNextClicked = { repository.stepNext() },
            onCopyClicked = {
                val prompt = repository.markCurrentCopied()
                if (prompt != null) {
                    ClipboardHelper.writePlainText(this, "PromptShift", prompt)
                }
            },
            onCollapsedChanged = { collapsed -> repository.setCollapsed(collapsed) },
            onPositionCommitted = { x, y -> repository.updateOverlayPosition(x, y) },
        )

        val savedOverlay = repository.overlayState.value
        controller.attach(
            initialX = savedOverlay.x,
            initialY = savedOverlay.y,
            hasSavedPosition = savedOverlay.hasSavedPosition,
            collapsed = savedOverlay.isCollapsed,
        )
        overlayController = controller
        app.setOverlayRunning(true)

        serviceScope.launch {
            repository.batchState.collect { batch ->
                controller.renderBatchState(batch)
                updateNotification(batch)
            }
        }

        serviceScope.launch {
            repository.overlayState.collect { overlay ->
                controller.setCollapsed(overlay.isCollapsed)
            }
        }

        serviceScope.launch {
            repository.feedbackEvents.collect { feedback ->
                controller.showFeedback(feedback)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlayController?.onDisplayBoundsChanged()
    }

    override fun onDestroy() {
        PromptShiftApp.from(this).setOverlayRunning(false)
        serviceScope.cancel()
        overlayController?.detach()
        overlayController = null
        super.onDestroy()
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
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(batchState: PromptBatchState): Notification {
        val openHubIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openHubPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openHubIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = if (batchState.isEmpty) {
            "Ready — tap Load on the bubble"
        } else {
            "Slot #${batchState.displayIndex}/${batchState.totalCount} • ${batchState.copiedCount}/${batchState.totalCount} copied"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_promptshift)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openHubPendingIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    private fun updateNotification(batchState: PromptBatchState) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(batchState))
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "promptshift_overlay_channel"
        private const val NOTIFICATION_ID = 1004
        const val ACTION_START = "com.promptshift.app.action.START_OVERLAY"
        const val ACTION_STOP = "com.promptshift.app.action.STOP_OVERLAY"

        fun start(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}
