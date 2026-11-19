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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.domain.BatchLoadResult
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.InstructionImportResult
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.overlay.FloatingOverlayService
import com.promptshift.app.ui.theme.PromptShiftTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var readinessState by mutableStateOf(
        RealmeReadinessState(
            canDrawOverlays = false,
            isIgnoringBatteryOptimizations = false,
        ),
    )

    private var bannerMessageState by mutableStateOf<String?>(null)
    private var bannerIsErrorState by mutableStateOf(false)
    private var pendingStartAfterNotificationPrompt = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        if (pendingStartAfterNotificationPrompt) {
            pendingStartAfterNotificationPrompt = false
            startOverlayIfPermitted()
        }
    }

    private val exportInstructionsLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = PromptShiftApp.from(this).aiConfigRepository.exportInstructionsJson()
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            showBanner("Exported Instruction Profiles (no API keys)", isError = false)
        } catch (_: Exception) {
            showBanner("Could not export Instruction Profiles", isError = true)
        }
    }

    private val importInstructionsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            when (val res = PromptShiftApp.from(this).aiConfigRepository.importInstructionsJson(json)) {
                is InstructionImportResult.Imported -> {
                    showBanner("Imported ${res.count} instruction preset${if (res.count == 1) "" else "s"}", isError = false)
                }
                is InstructionImportResult.Failed -> {
                    showBanner(res.reason, isError = true)
                }
            }
        } catch (_: Exception) {
            showBanner("Could not read selected file", isError = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        configureRealmeEdgeToEdge()
        super.onCreate(savedInstanceState)

        refreshReadiness()
        val app = PromptShiftApp.from(this)
        val repository = app.repository
        val aiConfigRepository = app.aiConfigRepository
        val coordinator = app.generationCoordinator

        setContent {
            PromptShiftTheme {
                val batchState by repository.batchState.collectAsStateWithLifecycle()
                val aiConfig by aiConfigRepository.configState.collectAsStateWithLifecycle()
                val generationState by coordinator.generationState.collectAsStateWithLifecycle()
                val isOverlayRunning by app.isOverlayRunning.collectAsStateWithLifecycle()

                val uiScope = rememberCoroutineScope()
                var testingProviderId by remember { mutableStateOf<String?>(null) }
                var connectionTestResults by remember {
                    mutableStateOf<Map<String, ConnectionTestResult>>(emptyMap())
                }

                LaunchedEffect(bannerMessageState) {
                    if (bannerMessageState != null) {
                        delay(2800L)
                        bannerMessageState = null
                    }
                }

                PromptShiftHubScreen(
                    batchState = batchState,
                    aiConfig = aiConfig,
                    generationState = generationState,
                    isOverlayRunning = isOverlayRunning,
                    readiness = readinessState,
                    statusBanner = bannerMessageState,
                    statusBannerIsError = bannerIsErrorState,
                    testingProviderId = testingProviderId,
                    connectionTestResults = connectionTestResults,
                    onToggleBubble = {
                        if (isOverlayRunning) {
                            FloatingOverlayService.stop(this)
                            showBanner("Floating bubble stopped", isError = false)
                        } else {
                            handleStartBubbleRequest(
                                onNeedOverlayPermission = {
                                    showBanner("Enable Display over other apps first", isError = true)
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
                    onOpenKeyboardSettings = {
                        RealmeReadinessHelper.openInputMethodSettings(this)
                    },
                    onSwitchKeyboard = {
                        if (!readinessState.isPromptShiftKeyboardEnabled) {
                            showBanner("Enable PromptShift Keyboard first", isError = true)
                            RealmeReadinessHelper.openInputMethodSettings(this)
                        } else {
                            RealmeReadinessHelper.showInputMethodPicker(this)
                        }
                    },
                    onLoadFromClipboard = {
                        val raw = ClipboardHelper.readPlainText(this)
                        when (val result = repository.loadFromRawText(raw)) {
                            is BatchLoadResult.Loaded -> {
                                showBanner("Loaded ${result.count} prompt${if (result.count == 1) "" else "s"}", isError = false)
                            }
                            BatchLoadResult.PreservedNoDelimiter,
                            BatchLoadResult.PreservedEmpty,
                            -> {
                                showBanner("No ===PROMPT=== found — existing batch preserved", isError = true)
                            }
                        }
                    },
                    onClearBatch = {
                        repository.clearBatch()
                        showBanner("Batch cleared", isError = false)
                    },
                    onCopyAiInstruction = {
                        val ok = ClipboardHelper.writePlainText(
                            context = this,
                            label = "PromptShift AI Format",
                            text = PromptFormatGuide.AI_FORMAT_INSTRUCTION,
                        )
                        showBanner(
                            if (ok) "Copied AI instruction ✅" else "Could not write clipboard",
                            isError = !ok,
                        )
                    },
                    onSelectProvider = { aiConfigRepository.selectProvider(it) },
                    onSaveProvider = { id, name, baseUrl, modelId, apiKeyInput ->
                        val saved = aiConfigRepository.saveProvider(id, name, baseUrl, modelId, apiKeyInput)
                        connectionTestResults = connectionTestResults - saved.id
                        showBanner("Saved provider ${saved.name}", isError = false)
                    },
                    onDeleteProvider = { id ->
                        aiConfigRepository.deleteProvider(id)
                        connectionTestResults = connectionTestResults - id
                        showBanner("Provider removed", isError = false)
                    },
                    onTestProvider = { providerId ->
                        if (testingProviderId != null) return@PromptShiftHubScreen
                        testingProviderId = providerId
                        uiScope.launch {
                            val result = coordinator.testConnection(providerId)
                            connectionTestResults = connectionTestResults + (providerId to result)
                            testingProviderId = null
                        }
                    },
                    onSelectInstruction = { aiConfigRepository.selectInstruction(it) },
                    onSaveInstruction = { id, name, body ->
                        val saved = aiConfigRepository.saveInstruction(id, name, body)
                        showBanner("Saved instruction ${saved.name}", isError = false)
                    },
                    onDeleteInstruction = { id ->
                        aiConfigRepository.deleteInstruction(id)
                        showBanner("Instruction removed", isError = false)
                    },
                    onExportInstructions = {
                        exportInstructionsLauncher.launch("promptshift-instructions.json")
                    },
                    onImportInstructions = {
                        importInstructionsLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    onStartGeneration = { direction, count, allowReplace ->
                        coordinator.startGeneration(
                            direction = direction,
                            promptCount = count,
                            allowReplaceExisting = allowReplace,
                        )
                    },
                    onCancelGeneration = {
                        coordinator.cancelGeneration()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshReadiness()
    }

    private fun showBanner(message: String, isError: Boolean) {
        bannerMessageState = message
        bannerIsErrorState = isError
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
