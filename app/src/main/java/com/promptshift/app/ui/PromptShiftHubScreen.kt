package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.FetchModelsResult
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.domain.ReasoningEffort
import com.promptshift.app.domain.StartGenerationResult
import com.promptshift.app.timer.TimerState
import com.promptshift.app.ui.theme.PromptShiftColors
import kotlinx.coroutines.delay

@Composable
fun PromptShiftHubScreen(
    batchState: PromptBatchState,
    aiConfig: AiConfigState,
    generationState: GenerationState,
    timerState: TimerState,
    isOverlayRunning: Boolean,
    readiness: RealmeReadinessState,
    enabledKeyboards: List<EnabledKeyboardOption>,
    statusBanner: String?,
    statusBannerIsError: Boolean,
    testingProviderId: String?,
    connectionTestResults: Map<String, ConnectionTestResult>,
    onToggleBubble: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onSelectPreferredReturnIme: (String?) -> Unit,
    onLoadFromClipboard: () -> Unit,
    onClearBatch: () -> Unit,
    onSaveCurrentSlotAsSnippet: () -> Unit,
    onCopyAiInstruction: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectProviderModel: (providerId: String, modelId: String) -> Unit,
    onSaveProvider: (
        id: String?,
        name: String,
        baseUrl: String,
        modelId: String,
        apiKeyInput: String?,
        favoriteModels: List<String>,
    ) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestProvider: (String) -> Unit,
    onFetchModels: suspend (baseUrl: String, apiKeyInput: String?, existingProviderId: String?) -> FetchModelsResult,
    onSelectInstruction: (String?) -> Unit,
    onSaveInstruction: (id: String?, name: String, body: String) -> Unit,
    onDeleteInstruction: (String) -> Unit,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    onSetActiveTriggerWord: (String?, Boolean?) -> Unit,
    onToggleTriggerEnabled: (Boolean) -> Unit,
    onToggleTriggerChip: (String) -> Unit,
    onSaveTriggerChip: (String) -> Unit,
    onRemoveTriggerChip: (String) -> Unit,
    onSaveSnippet: (id: String?, title: String, body: String, includeTriggerWord: Boolean) -> Unit,
    onDeleteSnippet: (String) -> Unit,
    onCopySnippet: (PromptSnippet) -> Unit,
    onCopyRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onClearRecentDirections: () -> Unit,
    onToggleTimerPill: (Boolean) -> Unit,
    onStartTimer: (minutes: Int, seconds: Int) -> Unit,
    onStartTimerPreset: (totalSeconds: Int) -> Unit,
    onSaveTimerPreset: (minutes: Int, seconds: Int) -> Boolean,
    onRemoveTimerPreset: (totalSeconds: Int) -> Unit,
    onCancelTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onDismissTimerAlert: () -> Unit,
    onExportSafeBackup: () -> Unit,
    onImportSafeBackup: () -> Unit,
    onStartGeneration: (direction: String, count: Int, allowReplace: Boolean) -> StartGenerationResult,
    onCancelGeneration: () -> Unit,
) {
    var awaitingClearConfirm by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderProfile?>(null) }
    var showInstructionDialog by remember { mutableStateOf(false) }
    var editingInstruction by remember { mutableStateOf<InstructionProfile?>(null) }
    var showSnippetDialog by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<PromptSnippet?>(null) }
    var showReturnKeyboardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(awaitingClearConfirm) {
        if (awaitingClearConfirm) {
            delay(3000L)
            awaitingClearConfirm = false
        }
    }

    LaunchedEffect(batchState.isEmpty) {
        if (batchState.isEmpty) {
            awaitingClearConfirm = false
        }
    }

    // Full-bleed Obsidian background extends seamlessly behind status bar & punch-hole camera cutout.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PromptShiftColors.ObsidianBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. Header: PROMPT (white) + SHIFT (GT Crimson)
            HeaderWordmark(
                isOverlayRunning = isOverlayRunning,
                isStreaming = generationState.isStreaming,
            )

            // Optional transient inline feedback banner
            if (!statusBanner.isNullOrBlank()) {
                FeedbackBanner(
                    message = statusBanner,
                    isError = statusBannerIsError,
                )
            }

            // 2. Hero Control Card
            HeroControlCard(
                isOverlayRunning = isOverlayRunning,
                canDrawOverlays = readiness.canDrawOverlays,
                onToggleBubble = onToggleBubble,
            )

            // 3. Realme Readiness Strip
            val preferredReturnKeyboardLabel = remember(aiConfig.preferredReturnImeId, enabledKeyboards) {
                val savedId = aiConfig.preferredReturnImeId
                when {
                    savedId.isNullOrBlank() -> "Auto (Previous)"
                    else -> enabledKeyboards.firstOrNull { it.id == savedId }?.label
                        ?: "Fallback (Auto)"
                }
            }
            RealmeReadinessStrip(
                readiness = readiness,
                preferredReturnKeyboardLabel = preferredReturnKeyboardLabel,
                hasPreferredReturnKeyboard = !aiConfig.preferredReturnImeId.isNullOrBlank(),
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenKeyboardSettings = onOpenKeyboardSettings,
                onSwitchKeyboard = onSwitchKeyboard,
                onPickReturnKeyboard = { showReturnKeyboardDialog = true },
            )

            // 4. Trigger Word Card (Phase 4 — pinned before every Copy & Insert)
            TriggerWordCard(
                aiConfig = aiConfig,
                onSetActiveTriggerWord = onSetActiveTriggerWord,
                onToggleTriggerEnabled = onToggleTriggerEnabled,
                onToggleTriggerChip = onToggleTriggerChip,
                onSaveTriggerChip = onSaveTriggerChip,
                onRemoveTriggerChip = onRemoveTriggerChip,
            )

            // 5. Direct AI Generate Card (Phase 2 + Phase 4 Fav Models, Efforts, Snippet append)
            DirectAiGenerateCard(
                aiConfig = aiConfig,
                generationState = generationState,
                onSelectProvider = onSelectProvider,
                onSelectProviderModel = onSelectProviderModel,
                onSelectInstruction = onSelectInstruction,
                onSelectReasoningEffort = onSelectReasoningEffort,
                onClearRecentDirections = onClearRecentDirections,
                onAddProviderClick = {
                    editingProvider = null
                    showProviderDialog = true
                },
                onStartGeneration = onStartGeneration,
                onCancelGeneration = onCancelGeneration,
            )

            // 6. Active Batch Card (v1 manual clipboard load + clear + Phase 4 [+Snippet] & Trigger Word sync)
            ActiveBatchCard(
                batchState = batchState,
                activeTriggerWord = aiConfig.effectiveTriggerWord,
                isStreaming = generationState.isStreaming,
                awaitingClearConfirm = awaitingClearConfirm,
                onLoadFromClipboard = onLoadFromClipboard,
                onSaveCurrentSlotAsSnippet = onSaveCurrentSlotAsSnippet,
                onClearClick = {
                    if (awaitingClearConfirm) {
                        awaitingClearConfirm = false
                        onClearBatch()
                    } else {
                        awaitingClearConfirm = true
                    }
                },
            )

            // 7. Snippets & Recent 10 Card (Phase 4)
            SnippetsAndRecentCard(
                aiConfig = aiConfig,
                onAddSnippet = {
                    editingSnippet = null
                    showSnippetDialog = true
                },
                onEditSnippet = { snippet ->
                    editingSnippet = snippet
                    showSnippetDialog = true
                },
                onDeleteSnippet = onDeleteSnippet,
                onCopySnippet = onCopySnippet,
                onCopyRecent = onCopyRecent,
                onClearRecent = onClearRecent,
            )

            // 8. Simple Countdown Timer Card (Phase 4)
            CountdownTimerCard(
                timerState = timerState,
                presetsSeconds = aiConfig.timerPresetsSeconds,
                timerPillEnabled = aiConfig.timerPillEnabled,
                onToggleTimerPill = onToggleTimerPill,
                onStartTimer = onStartTimer,
                onStartPreset = onStartTimerPreset,
                onSavePreset = onSaveTimerPreset,
                onRemovePreset = onRemoveTimerPreset,
                onCancelTimer = onCancelTimer,
                onResetTimer = onResetTimer,
                onDismissAlert = onDismissTimerAlert,
            )

            // 9. Providers & Instruction Profiles Manager Card (Phase 2 + Phase 4 Expand/Collapse & Backup v2)
            ProfilesManagerCard(
                aiConfig = aiConfig,
                testingProviderId = testingProviderId,
                connectionTestResults = connectionTestResults,
                onSelectProvider = onSelectProvider,
                onAddProvider = {
                    editingProvider = null
                    showProviderDialog = true
                },
                onEditProvider = { provider ->
                    editingProvider = provider
                    showProviderDialog = true
                },
                onDeleteProvider = onDeleteProvider,
                onTestProvider = onTestProvider,
                onSelectInstruction = onSelectInstruction,
                onAddInstruction = {
                    editingInstruction = null
                    showInstructionDialog = true
                },
                onEditInstruction = { inst ->
                    editingInstruction = inst
                    showInstructionDialog = true
                },
                onDeleteInstruction = onDeleteInstruction,
                onExportSafeBackup = onExportSafeBackup,
                onImportSafeBackup = onImportSafeBackup,
            )

            // 10. AI Format Helper Card
            AiFormatHelperCard(
                onCopyAiInstruction = onCopyAiInstruction,
            )

            // Subtle safety footer
            Text(
                text = "Copy-only & Keyboard Auto-Insert • Banking-app safe",
                color = PromptShiftColors.TextMuted.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }

    if (showProviderDialog) {
        ProviderProfileEditorDialog(
            initial = editingProvider,
            onDismiss = { showProviderDialog = false },
            onFetchModels = onFetchModels,
            onSave = { id, name, baseUrl, modelId, apiKey, favoriteModels ->
                onSaveProvider(id, name, baseUrl, modelId, apiKey, favoriteModels)
                showProviderDialog = false
            },
        )
    }

    if (showInstructionDialog) {
        InstructionProfileEditorDialog(
            initial = editingInstruction,
            onDismiss = { showInstructionDialog = false },
            onSave = { id, name, body ->
                onSaveInstruction(id, name, body)
                showInstructionDialog = false
            },
        )
    }

    if (showSnippetDialog) {
        SnippetEditorDialog(
            initial = editingSnippet,
            onDismiss = { showSnippetDialog = false },
            onSave = { id, title, body, includeTrigger ->
                onSaveSnippet(id, title, body, includeTrigger)
                showSnippetDialog = false
            },
        )
    }

    if (showReturnKeyboardDialog) {
        ReturnKeyboardPickerDialog(
            selectedImeId = aiConfig.preferredReturnImeId,
            enabledKeyboards = enabledKeyboards,
            onSelect = { imeId ->
                onSelectPreferredReturnIme(imeId)
                showReturnKeyboardDialog = false
            },
            onDismiss = { showReturnKeyboardDialog = false },
        )
    }
}

@Composable
internal fun SegmentTabButton(
    modifier: Modifier = Modifier,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) PromptShiftColors.CarbonElevated else Color.Transparent)
            .border(
                1.dp,
                if (selected) PromptShiftColors.MetalBorder else Color.Transparent,
                RoundedCornerShape(9.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (selected) PromptShiftColors.TextPrimary else PromptShiftColors.TextMuted,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun MiniActionChip(
    label: String,
    enabled: Boolean = true,
    isDanger: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PromptShiftColors.CarbonElevated)
            .border(
                1.dp,
                if (isDanger) PromptShiftColors.QuotaWarnRed.copy(alpha = 0.45f) else PromptShiftColors.MetalBorder,
                RoundedCornerShape(8.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> PromptShiftColors.TextMuted.copy(alpha = 0.45f)
                isDanger -> PromptShiftColors.QuotaWarnRed
                else -> PromptShiftColors.TextPrimary
            },
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun CarbonCard(
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PromptShiftColors.CarbonSurface,
        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            content()
        }
    }
}
