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
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.domain.SafeBackupImportResult
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
    private var enabledKeyboardsState by mutableStateOf<List<EnabledKeyboardOption>>(emptyList())

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

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = PromptShiftApp.from(this).aiConfigRepository.exportSafeBackupJson()
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            showBanner("Exported Safe Backup JSON (zero API keys)", isError = false)
        } catch (_: Exception) {
            showBanner("Could not export Safe Backup JSON", isError = true)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()
            when (val res = PromptShiftApp.from(this).aiConfigRepository.importSafeBackupJson(json)) {
                is SafeBackupImportResult.Imported -> {
                    showBanner(res.summaryLabel(), isError = false)
                }
                is SafeBackupImportResult.Failed -> {
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
        val timerManager = app.timerManager

        setContent {
            PromptShiftTheme {
                val batchState by repository.batchState.collectAsStateWithLifecycle()
                val aiConfig by aiConfigRepository.configState.collectAsStateWithLifecycle()
                val generationState by coordinator.generationState.collectAsStateWithLifecycle()
                val timerState by timerManager.state.collectAsStateWithLifecycle()
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
                    timerState = timerState,
                    isOverlayRunning = isOverlayRunning,
                    readiness = readinessState,
                    enabledKeyboards = enabledKeyboardsState,
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
                    onSelectPreferredReturnIme = { imeId ->
                        aiConfigRepository.setPreferredReturnImeId(imeId)
                        val label = if (imeId.isNullOrBlank()) {
                            "Auto (Previous)"
                        } else {
                            enabledKeyboardsState.firstOrNull { it.id == imeId }?.label ?: "Selected keyboard"
                        }
                        showBanner("Return keyboard: $label", isError = false)
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
                    onSaveCurrentSlotAsSnippet = {
                        val currentPrompt = batchState.currentPrompt
                        if (!currentPrompt.isNullOrBlank()) {
                            val autoTitle = currentPrompt.trim()
                                .replace('\n', ' ')
                                .take(24)
                                .ifBlank { "Slot #${batchState.displayIndex}" }
                            aiConfigRepository.saveSnippet(
                                id = null,
                                title = autoTitle,
                                body = currentPrompt,
                                includeTriggerWord = true,
                            )
                            showBanner("Saved slot #${batchState.displayIndex} as snippet 📌", isError = false)
                        }
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
                    onSelectProviderModel = { providerId, modelId ->
                        aiConfigRepository.selectProviderModel(providerId, modelId)
                        showBanner("Model: $modelId", isError = false)
                    },
                    onSaveProvider = { id, name, baseUrl, modelId, apiKeyInput, favoriteModels ->
                        val saved = aiConfigRepository.saveProvider(
                            id = id,
                            name = name,
                            baseUrl = baseUrl,
                            modelId = modelId,
                            apiKeyInput = apiKeyInput,
                            favoriteModels = favoriteModels,
                        )
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
                    onFetchModels = { baseUrl, apiKeyInput, existingProviderId ->
                        coordinator.fetchModelsRaw(baseUrl, apiKeyInput, existingProviderId)
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
                    onSelectReasoningEffort = { effort ->
                        aiConfigRepository.setReasoningEffort(effort)
                    },
                    onSetActiveTriggerWord = { rawWord, enabled ->
                        aiConfigRepository.setActiveTriggerWord(rawWord, enabled)
                    },
                    onToggleTriggerEnabled = { enabled ->
                        aiConfigRepository.setTriggerWordEnabled(enabled)
                    },
                    onToggleTriggerChip = { chip ->
                        aiConfigRepository.toggleTriggerWordChip(chip)
                    },
                    onSaveTriggerChip = { rawWord ->
                        if (aiConfigRepository.addTriggerWordChip(rawWord)) {
                            showBanner("Saved Trigger Word chip", isError = false)
                        }
                    },
                    onRemoveTriggerChip = { chip ->
                        aiConfigRepository.removeTriggerWordChip(chip)
                    },
                    onSaveSnippet = { id, title, body, includeTrigger ->
                        val saved = aiConfigRepository.saveSnippet(id, title, body, includeTrigger)
                        showBanner("Saved snippet ${saved.title}", isError = false)
                    },
                    onDeleteSnippet = { id ->
                        aiConfigRepository.deleteSnippet(id)
                        showBanner("Snippet removed", isError = false)
                    },
                    onCopySnippet = { snippet ->
                        val text = if (snippet.includeTriggerWord) {
                            PromptTextComposer.applyTriggerWord(snippet.body, aiConfig.effectiveTriggerWord)
                        } else {
                            snippet.body.trim()
                        }
                        val ok = ClipboardHelper.writePlainText(this, "PromptShift Snippet", text)
                        if (ok) aiConfigRepository.recordRecentPrompt(text)
                        showBanner(
                            if (ok) "Copied snippet: ${snippet.title}" else "Could not write clipboard",
                            isError = !ok,
                        )
                    },
                    onCopyRecent = { recent ->
                        val ok = ClipboardHelper.writePlainText(this, "PromptShift Recent", recent)
                        if (ok) aiConfigRepository.recordRecentPrompt(recent)
                        showBanner(
                            if (ok) "Copied recent prompt ✅" else "Could not write clipboard",
                            isError = !ok,
                        )
                    },
                    onClearRecent = {
                        aiConfigRepository.clearRecentPrompts()
                        showBanner("Recent 10 cleared", isError = false)
                    },
                    onClearRecentDirections = {
                        aiConfigRepository.clearRecentDirections()
                        showBanner("Recent directions cleared", isError = false)
                    },
                    onToggleTimerPill = { enabled ->
                        aiConfigRepository.setTimerPillEnabled(enabled)
                        showBanner(
                            if (enabled) "Floating countdown pill ON" else "Floating pill OFF (notification only)",
                            isError = false,
                        )
                    },
                    onStartTimer = { min, sec ->
                        if (timerManager.start(min, sec)) {
                            showBanner("Timer started (${"%02d:%02d".format(min, sec)})", isError = false)
                        }
                    },
                    onStartTimerPreset = { totalSeconds ->
                        if (timerManager.startTotalSeconds(totalSeconds)) {
                            val label = com.promptshift.app.timer.TimerState.formatPresetLabel(totalSeconds)
                            showBanner("Timer started ($label)", isError = false)
                        }
                    },
                    onSaveTimerPreset = { min, sec ->
                        aiConfigRepository.addTimerPreset(min, sec)
                    },
                    onRemoveTimerPreset = { totalSeconds ->
                        aiConfigRepository.removeTimerPreset(totalSeconds)
                        showBanner("Timer preset removed", isError = false)
                    },
                    onCancelTimer = {
                        timerManager.cancel()
                        showBanner("Timer cancelled", isError = false)
                    },
                    onResetTimer = {
                        timerManager.reset()
                        showBanner("Timer reset", isError = false)
                    },
                    onDismissTimerAlert = {
                        timerManager.dismissFinishedAlert()
                    },
                    onExportSafeBackup = {
                        exportBackupLauncher.launch("promptshift-backup.json")
                    },
                    onImportSafeBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
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
        enabledKeyboardsState = RealmeReadinessHelper.getOtherEnabledKeyboards(this)
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
