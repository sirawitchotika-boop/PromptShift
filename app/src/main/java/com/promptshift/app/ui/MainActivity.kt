package com.promptshift.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.domain.BatchLoadResult
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.overlay.FloatingOverlayService
import com.promptshift.app.ui.theme.PromptShiftTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var readinessState by mutableStateOf(
        RealmeReadinessState(
            canDrawOverlays = false,
            isIgnoringBatteryOptimizations = false,
        ),
    )

    private var pendingStartAfterNotificationPrompt = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        if (pendingStartAfterNotificationPrompt) {
            pendingStartAfterNotificationPrompt = false
            startOverlayIfPermitted()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        configureRealmeEdgeToEdge()
        super.onCreate(savedInstanceState)

        refreshReadiness()
        val app = PromptShiftApp.from(this)
        val repository = app.repository

        setContent {
            PromptShiftTheme {
                val batchState by repository.batchState.collectAsStateWithLifecycle()
                val isOverlayRunning by app.isOverlayRunning.collectAsStateWithLifecycle()

                var bannerMessage by remember { mutableStateOf<String?>(null) }
                var bannerIsError by remember { mutableStateOf(false) }

                LaunchedEffect(bannerMessage) {
                    if (bannerMessage != null) {
                        delay(2600L)
                        bannerMessage = null
                    }
                }

                PromptShiftHubScreen(
                    batchState = batchState,
                    isOverlayRunning = isOverlayRunning,
                    readiness = readinessState,
                    statusBanner = bannerMessage,
                    statusBannerIsError = bannerIsError,
                    onToggleBubble = {
                        if (isOverlayRunning) {
                            FloatingOverlayService.stop(this)
                            bannerMessage = "Floating bubble stopped"
                            bannerIsError = false
                        } else {
                            handleStartBubbleRequest(
                                onNeedOverlayPermission = {
                                    bannerMessage = "Enable Display over other apps first"
                                    bannerIsError = true
                                },
                            )
                        }
                    },
                    onOpenOverlaySettings = {
                        RealmeReadinessHelper.openOverlayPermissionSettings(this)
                    },
                    onOpenBatterySettings = {
                        RealmeReadinessHelper.openBatteryAndBackgroundSettings(this)
                    },
                    onLoadFromClipboard = {
                        val raw = ClipboardHelper.readPlainText(this)
                        when (val result = repository.loadFromRawText(raw)) {
                            is BatchLoadResult.Loaded -> {
                                bannerMessage = "Loaded ${result.count} prompt${if (result.count == 1) "" else "s"}"
                                bannerIsError = false
                            }
                            BatchLoadResult.PreservedNoDelimiter,
                            BatchLoadResult.PreservedEmpty,
                            -> {
                                bannerMessage = "No ===PROMPT=== found — existing batch preserved"
                                bannerIsError = true
                            }
                        }
                    },
                    onClearBatch = {
                        repository.clearBatch()
                        bannerMessage = "Batch cleared"
                        bannerIsError = false
                    },
                    onCopyAiInstruction = {
                        val ok = ClipboardHelper.writePlainText(
                            context = this,
                            label = "PromptShift NanoGPT Format",
                            text = PromptFormatGuide.NANO_GPT_INSTRUCTION,
                        )
                        bannerMessage = if (ok) "Copied AI instruction ✅" else "Could not write clipboard"
                        bannerIsError = !ok
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshReadiness()
    }

    @Suppress("DEPRECATION")
    private fun configureRealmeEdgeToEdge() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun refreshReadiness() {
        readinessState = RealmeReadinessHelper.checkState(this)
    }

    private fun handleStartBubbleRequest(onNeedOverlayPermission: () -> Unit) {
        refreshReadiness()
        if (!readinessState.canDrawOverlays) {
            onNeedOverlayPermission()
            RealmeReadinessHelper.openOverlayPermissionSettings(this)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                pendingStartAfterNotificationPrompt = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        startOverlayIfPermitted()
    }

    private fun startOverlayIfPermitted() {
        refreshReadiness()
        if (readinessState.canDrawOverlays) {
            FloatingOverlayService.start(this)
        }
    }
}
