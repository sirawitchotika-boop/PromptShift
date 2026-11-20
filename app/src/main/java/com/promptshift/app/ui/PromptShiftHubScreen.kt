package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.FetchModelsResult
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.PromptTextComposer
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
    statusBanner: String?,
    statusBannerIsError: Boolean,
    testingProviderId: String?,
    connectionTestResults: Map<String, ConnectionTestResult>,
    onToggleBubble: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onSwitchKeyboard: () -> Unit,
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
    onStartTimer: (minutes: Int, seconds: Int) -> Unit,
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
            RealmeReadinessStrip(
                readiness = readiness,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenKeyboardSettings = onOpenKeyboardSettings,
                onSwitchKeyboard = onSwitchKeyboard,
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
                onStartTimer = onStartTimer,
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
}

@Composable
private fun HeaderWordmark(
    isOverlayRunning: Boolean,
    isStreaming: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = PromptShiftColors.TextPrimary)) {
                    append("PROMPT")
                }
                withStyle(SpanStyle(color = PromptShiftColors.GtCrimson)) {
                    append("SHIFT")
                }
            },
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.4.sp,
        )

        val dotColor = when {
            isStreaming -> PromptShiftColors.GtCrimsonGlow
            isOverlayRunning -> PromptShiftColors.CopiedEmerald
            else -> PromptShiftColors.TextMuted
        }
        val pillText = when {
            isStreaming -> "STREAMING"
            isOverlayRunning -> "ACTIVE"
            else -> "STANDBY"
        }
        val textColor = when {
            isStreaming -> PromptShiftColors.GtCrimsonGlow
            isOverlayRunning -> PromptShiftColors.CopiedEmerald
            else -> PromptShiftColors.TextMuted
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(PromptShiftColors.CarbonSurface)
                .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = pillText,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun FeedbackBanner(message: String, isError: Boolean) {
    val bgColor = if (isError) PromptShiftColors.QuotaWarnBg else PromptShiftColors.CopiedEmeraldBg
    val borderColor = if (isError) PromptShiftColors.QuotaWarnRed else PromptShiftColors.CopiedEmerald
    val textColor = if (isError) PromptShiftColors.QuotaWarnRed else PromptShiftColors.CopiedEmerald

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HeroControlCard(
    isOverlayRunning: Boolean,
    canDrawOverlays: Boolean,
    onToggleBubble: () -> Unit,
) {
    CarbonCard {
        Text(
            text = "FLOATING OVERLAY",
            color = PromptShiftColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when {
                isOverlayRunning -> "Bubble is floating over your apps. Tap AI to stream prompts or Load from clipboard anytime."
                canDrawOverlays -> "Launch the floating pill before switching apps so you can Generate, Load, and Copy prompts in one tap."
                else -> "Grant Display over other apps below, then start the floating bubble."
            },
            color = PromptShiftColors.TextPrimary.copy(alpha = 0.88f),
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onToggleBubble,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isOverlayRunning) PromptShiftColors.CarbonElevated else PromptShiftColors.GtCrimson,
                contentColor = PromptShiftColors.TextPrimary,
            ),
            border = if (isOverlayRunning) {
                BorderStroke(1.5.dp, PromptShiftColors.GtCrimson)
            } else {
                BorderStroke(1.dp, PromptShiftColors.GtCrimsonGlow)
            },
        ) {
            Text(
                text = if (isOverlayRunning) "Stop Bubble" else "Start Floating Bubble",
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun RealmeReadinessStrip(
    readiness: RealmeReadinessState,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onSwitchKeyboard: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadinessChip(
                modifier = Modifier.weight(1f),
                title = "Display over apps",
                statusText = if (readiness.canDrawOverlays) "Ready" else "Grant →",
                isReady = readiness.canDrawOverlays,
                accentWhenPending = PromptShiftColors.GtCrimson,
                onClick = onOpenOverlaySettings,
            )
            ReadinessChip(
                modifier = Modifier.weight(1f),
                title = "Battery / Background",
                statusText = if (readiness.isIgnoringBatteryOptimizations) "Unrestricted" else "Settings →",
                isReady = readiness.isIgnoringBatteryOptimizations,
                accentWhenPending = PromptShiftColors.TextMuted,
                onClick = onOpenBatterySettings,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadinessChip(
                modifier = Modifier.weight(1f),
                title = "Auto-Insert Keyboard",
                statusText = when {
                    readiness.isPromptShiftKeyboardActive -> "Active"
                    readiness.isPromptShiftKeyboardEnabled -> "Enabled"
                    else -> "Enable →"
                },
                isReady = readiness.isPromptShiftKeyboardEnabled,
                accentWhenPending = PromptShiftColors.GtCrimson,
                onClick = onOpenKeyboardSettings,
            )
            ReadinessChip(
                modifier = Modifier.weight(1f),
                title = "Switch Keyboard",
                statusText = if (readiness.isPromptShiftKeyboardActive) "PromptShift ⇄" else "Picker →",
                isReady = readiness.isPromptShiftKeyboardActive,
                accentWhenPending = PromptShiftColors.TextMuted,
                onClick = onSwitchKeyboard,
            )
        }
    }
}

@Composable
private fun ReadinessChip(
    modifier: Modifier = Modifier,
    title: String,
    statusText: String,
    isReady: Boolean,
    accentWhenPending: Color,
    onClick: () -> Unit,
) {
    val borderColor = if (isReady) {
        PromptShiftColors.CopiedEmerald.copy(alpha = 0.55f)
    } else {
        PromptShiftColors.MetalBorder
    }
    val statusColor = if (isReady) PromptShiftColors.CopiedEmerald else accentWhenPending

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PromptShiftColors.CarbonSurface)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = PromptShiftColors.TextPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = statusText,
            color = statusColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DirectAiGenerateCard(
    aiConfig: AiConfigState,
    generationState: GenerationState,
    onSelectProvider: (String) -> Unit,
    onSelectProviderModel: (providerId: String, modelId: String) -> Unit,
    onSelectInstruction: (String?) -> Unit,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    onAddProviderClick: () -> Unit,
    onStartGeneration: (String, Int, Boolean) -> StartGenerationResult,
    onCancelGeneration: () -> Unit,
) {
    var direction by remember { mutableStateOf("") }
    var selectedCount by remember(aiConfig.defaultPromptCount) {
        mutableIntStateOf(aiConfig.defaultPromptCount)
    }
    var customMode by remember {
        mutableStateOf(aiConfig.defaultPromptCount !in listOf(5, 10, 20))
    }
    var customInput by remember(aiConfig.defaultPromptCount) {
        mutableStateOf(aiConfig.defaultPromptCount.toString())
    }
    var pendingOverwriteCount by remember { mutableStateOf<Int?>(null) }
    var inlineError by remember { mutableStateOf<String?>(null) }

    val effectiveCount = if (customMode) {
        customInput.toIntOrNull()?.coerceIn(1, 100) ?: selectedCount
    } else {
        selectedCount
    }

    CarbonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DIRECT AI GENERATE",
                color = PromptShiftColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = "SSE Live Unlock",
                color = PromptShiftColors.CopiedEmerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (aiConfig.providers.isEmpty()) {
            Text(
                text = "Connect any OpenAI-compatible AI provider to stream prompt batches straight into the floating bubble.",
                color = PromptShiftColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAddProviderClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PromptShiftColors.GtCrimson),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PromptShiftColors.CarbonElevated,
                    contentColor = PromptShiftColors.TextPrimary,
                ),
            ) {
                Text("+ Add First AI Provider", fontWeight = FontWeight.Bold)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactSelectorSection(label = "ACTIVE PROVIDER") {
                    aiConfig.providers.forEach { provider ->
                        SelectablePillChip(
                            label = provider.name,
                            selected = provider.id == aiConfig.activeProvider?.id,
                            onClick = { onSelectProvider(provider.id) },
                        )
                    }
                }

                val activeProvider = aiConfig.activeProvider
                if (activeProvider != null && activeProvider.allSelectableModels.isNotEmpty()) {
                    CompactSelectorSection(label = "FAVORITE MODEL") {
                        activeProvider.allSelectableModels.forEach { model ->
                            SelectablePillChip(
                                label = model,
                                selected = model == activeProvider.modelId,
                                onClick = { onSelectProviderModel(activeProvider.id, model) },
                            )
                        }
                    }
                }

                CompactSelectorSection(label = "INSTRUCTION PRESET") {
                    SelectablePillChip(
                        label = "None",
                        selected = aiConfig.activeInstructionId == null,
                        onClick = { onSelectInstruction(null) },
                    )
                    aiConfig.instructions.forEach { inst ->
                        SelectablePillChip(
                            label = inst.name,
                            selected = inst.id == aiConfig.activeInstructionId,
                            onClick = { onSelectInstruction(inst.id) },
                        )
                    }
                }

                CompactSelectorSection(label = "REASONING EFFORT") {
                    ReasoningEffort.entries.forEach { effort ->
                        SelectablePillChip(
                            label = effort.label,
                            selected = aiConfig.reasoningEffort == effort,
                            onClick = { onSelectReasoningEffort(effort) },
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "PROMPT COUNT",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.9.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(5, 10, 20).forEach { count ->
                            SelectablePillChip(
                                label = count.toString(),
                                selected = !customMode && selectedCount == count,
                                onClick = {
                                    customMode = false
                                    selectedCount = count
                                    customInput = count.toString()
                                },
                            )
                        }
                        SelectablePillChip(
                            label = "Custom",
                            selected = customMode,
                            onClick = { customMode = true },
                        )
                        if (customMode) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { raw -> customInput = raw.filter { it.isDigit() }.take(3) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(72.dp),
                                colors = darkInputColors(),
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = direction,
                    onValueChange = {
                        direction = it
                        inlineError = null
                    },
                    label = { Text("Quick Direction", fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            "Describe your batch idea or theme...",
                            color = PromptShiftColors.TextMuted.copy(alpha = 0.65f),
                            fontSize = 12.5.sp,
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )

                if (aiConfig.snippets.isNotEmpty()) {
                    CompactSelectorSection(label = "TAP SNIPPET TO APPEND") {
                        aiConfig.snippets.forEach { snippet ->
                            SelectablePillChip(
                                label = "+ ${snippet.title}",
                                selected = false,
                                onClick = {
                                    direction = PromptTextComposer.appendToDraft(direction, snippet.body)
                                    inlineError = null
                                },
                            )
                        }
                    }
                }

                // Live streaming progress
                when (generationState) {
                    is GenerationState.Streaming -> {
                        val progress = (generationState.unlockedCount.toFloat() / generationState.targetCount.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Streaming from ${generationState.providerName}: ${generationState.unlockedCount}/${generationState.targetCount} unlocked",
                                color = PromptShiftColors.CopiedEmerald,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = PromptShiftColors.CopiedEmerald,
                                trackColor = PromptShiftColors.ObsidianBg,
                            )
                        }
                    }
                    is GenerationState.Error -> {
                        Text(
                            text = generationState.message,
                            color = PromptShiftColors.QuotaWarnRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    else -> Unit
                }

                if (!inlineError.isNullOrBlank()) {
                    Text(
                        text = inlineError.orEmpty(),
                        color = PromptShiftColors.QuotaWarnRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Overwrite Guard confirmation
                val uncopied = pendingOverwriteCount
                if (uncopied != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PromptShiftColors.QuotaWarnBg)
                            .border(1.dp, PromptShiftColors.QuotaWarnRed.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Current batch still has $uncopied uncopied prompt${if (uncopied == 1) "" else "s"}. Replace and stream live?",
                                color = PromptShiftColors.TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        pendingOverwriteCount = null
                                        when (val res = onStartGeneration(direction, effectiveCount, true)) {
                                            StartGenerationResult.Started -> inlineError = null
                                            is StartGenerationResult.Blocked -> inlineError = res.reason
                                            is StartGenerationResult.NeedsOverwriteConfirmation -> Unit
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PromptShiftColors.GtCrimson,
                                        contentColor = PromptShiftColors.TextPrimary,
                                    ),
                                ) {
                                    Text("Replace & Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { pendingOverwriteCount = null },
                                    modifier = Modifier.weight(0.75f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextMuted),
                                ) {
                                    Text("Keep", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (generationState.isStreaming) {
                    Button(
                        onClick = onCancelGeneration,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PromptShiftColors.CarbonElevated,
                            contentColor = PromptShiftColors.QuotaWarnRed,
                        ),
                        border = BorderStroke(1.5.dp, PromptShiftColors.QuotaWarnRed),
                    ) {
                        Text("Cancel Stream", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            inlineError = null
                            when (val res = onStartGeneration(direction, effectiveCount, false)) {
                                StartGenerationResult.Started -> pendingOverwriteCount = null
                                is StartGenerationResult.NeedsOverwriteConfirmation ->
                                    pendingOverwriteCount = res.uncopiedCount
                                is StartGenerationResult.Blocked -> inlineError = res.reason
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PromptShiftColors.GtCrimson,
                            contentColor = PromptShiftColors.TextPrimary,
                        ),
                        border = BorderStroke(1.dp, PromptShiftColors.GtCrimsonGlow),
                    ) {
                        Text(
                            text = "Generate $effectiveCount Prompts",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilesManagerCard(
    aiConfig: AiConfigState,
    testingProviderId: String?,
    connectionTestResults: Map<String, ConnectionTestResult>,
    onSelectProvider: (String) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (ProviderProfile) -> Unit,
    onDeleteProvider: (String) -> Unit,
    onTestProvider: (String) -> Unit,
    onSelectInstruction: (String?) -> Unit,
    onAddInstruction: () -> Unit,
    onEditInstruction: (InstructionProfile) -> Unit,
    onDeleteInstruction: (String) -> Unit,
    onExportSafeBackup: () -> Unit,
    onImportSafeBackup: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Providers, 1 = Instructions
    var expandedInstructionIds by remember { mutableStateOf(emptySet<String>()) }

    val canExportBackup = aiConfig.instructions.isNotEmpty() ||
        aiConfig.snippets.isNotEmpty() ||
        aiConfig.triggerWords.isNotEmpty() ||
        aiConfig.providers.any { it.favoriteModels.isNotEmpty() }

    CarbonCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PromptShiftColors.ObsidianBg)
                .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SegmentTabButton(
                modifier = Modifier.weight(1f),
                title = "Providers (${aiConfig.providers.size})",
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
            )
            SegmentTabButton(
                modifier = Modifier.weight(1f),
                title = "Instructions (${aiConfig.instructions.size})",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (aiConfig.providers.isEmpty()) {
                    Text(
                        text = "No provider profiles saved yet. Add OpenRouter, DeepSeek, OpenAI, or any OpenAI-compatible endpoint.",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    aiConfig.providers.forEach { provider ->
                        val isActive = provider.id == aiConfig.activeProvider?.id
                        val isTesting = testingProviderId == provider.id
                        val testResult = connectionTestResults[provider.id]

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PromptShiftColors.ObsidianBg)
                                .border(
                                    1.dp,
                                    if (isActive) PromptShiftColors.GtCrimson else PromptShiftColors.MetalBorder,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onSelectProvider(provider.id) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = provider.name,
                                    color = PromptShiftColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (isActive) "ACTIVE" else if (provider.hasApiKey) "KEY LOCKED" else "NO KEY",
                                    color = when {
                                        isActive -> PromptShiftColors.GtCrimsonGlow
                                        provider.hasApiKey -> PromptShiftColors.CopiedEmerald
                                        else -> PromptShiftColors.QuotaWarnRed
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = "${provider.modelId} • ${provider.baseUrl}",
                                color = PromptShiftColors.TextMuted,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (provider.favoriteModels.isNotEmpty()) {
                                Text(
                                    text = "★ ${provider.favoriteModels.size} favorite model${if (provider.favoriteModels.size == 1) "" else "s"} saved",
                                    color = PromptShiftColors.CopiedEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (testResult != null) {
                                val (msg, color) = when (testResult) {
                                    is ConnectionTestResult.Success ->
                                        testResult.detail to PromptShiftColors.CopiedEmerald
                                    is ConnectionTestResult.Failure ->
                                        testResult.message to PromptShiftColors.QuotaWarnRed
                                }
                                Text(
                                    text = msg,
                                    color = color,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                MiniActionChip(
                                    label = if (isTesting) "Testing..." else "Test",
                                    enabled = !isTesting,
                                    onClick = { onTestProvider(provider.id) },
                                )
                                MiniActionChip(
                                    label = "Models / Edit",
                                    onClick = { onEditProvider(provider) },
                                )
                                MiniActionChip(
                                    label = "Delete",
                                    isDanger = true,
                                    onClick = { onDeleteProvider(provider.id) },
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddProvider,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PromptShiftColors.CarbonElevated,
                        contentColor = PromptShiftColors.TextPrimary,
                    ),
                ) {
                    Text("+ Add Provider Profile", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (aiConfig.instructions.isEmpty()) {
                    Text(
                        text = "No saved Instruction Profiles yet. Save your reusable prompt rules here to switch styles in 1 tap.",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    aiConfig.instructions.forEach { inst ->
                        val isActive = inst.id == aiConfig.activeInstructionId
                        val isExpanded = inst.id in expandedInstructionIds
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PromptShiftColors.ObsidianBg)
                                .border(
                                    1.dp,
                                    if (isActive) PromptShiftColors.GtCrimson else PromptShiftColors.MetalBorder,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { onSelectInstruction(if (isActive) null else inst.id) }
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = inst.name,
                                    color = PromptShiftColors.TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isActive) {
                                    Text(
                                        text = "ACTIVE",
                                        color = PromptShiftColors.GtCrimsonGlow,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Text(
                                text = inst.body,
                                color = PromptShiftColors.TextMuted,
                                fontSize = 11.5.sp,
                                lineHeight = 16.5.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniActionChip(
                                    label = if (isActive) "Unselect" else "Use",
                                    onClick = { onSelectInstruction(if (isActive) null else inst.id) },
                                )
                                MiniActionChip(
                                    label = if (isExpanded) "Collapse ▴" else "Expand ▾",
                                    onClick = {
                                        expandedInstructionIds = if (isExpanded) {
                                            expandedInstructionIds - inst.id
                                        } else {
                                            expandedInstructionIds + inst.id
                                        }
                                    },
                                )
                                MiniActionChip(
                                    label = "Edit",
                                    onClick = { onEditInstruction(inst) },
                                )
                                MiniActionChip(
                                    label = "Delete",
                                    isDanger = true,
                                    onClick = { onDeleteInstruction(inst.id) },
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddInstruction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.75f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PromptShiftColors.CarbonElevated,
                        contentColor = PromptShiftColors.TextPrimary,
                    ),
                ) {
                    Text("+ Add Instruction Profile", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onExportSafeBackup,
                        enabled = canExportBackup,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextPrimary),
                    ) {
                        Text("Export JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onImportSafeBackup,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextPrimary),
                    ) {
                        Text("Import JSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Safe backup covers Instruction Profiles, Saved Snippets, Trigger Word Chips, and Favorite Model IDs — API keys are never exported.",
                    color = PromptShiftColors.TextMuted.copy(alpha = 0.78f),
                    fontSize = 11.sp,
                )
            }
        }
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
private fun ActiveBatchCard(
    batchState: PromptBatchState,
    activeTriggerWord: String?,
    isStreaming: Boolean,
    awaitingClearConfirm: Boolean,
    onLoadFromClipboard: () -> Unit,
    onSaveCurrentSlotAsSnippet: () -> Unit,
    onClearClick: () -> Unit,
) {
    val exceedsQuota = batchState.isCurrentExceedingQuota(activeTriggerWord)

    CarbonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ACTIVE BATCH",
                color = PromptShiftColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )

            val countColor = when {
                exceedsQuota -> PromptShiftColors.QuotaWarnRed
                activeTriggerWord != null && !batchState.isEmpty -> PromptShiftColors.CopiedEmerald
                else -> PromptShiftColors.TextMuted
            }
            Text(
                text = batchState.charCountLabel(activeTriggerWord),
                color = countColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (exceedsQuota) FontWeight.Bold else FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (batchState.isEmpty) {
            Text(
                text = if (isStreaming) "Waiting for slot #1..." else "No batch loaded yet",
                color = PromptShiftColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Generate directly with AI above, or copy a ===PROMPT=== batch from any AI chat and tap Load.",
                color = PromptShiftColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "${batchState.copiedCount} / ${batchState.totalCount} copied",
                        color = PromptShiftColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (batchState.isCurrentCopied) {
                                    PromptShiftColors.CopiedEmeraldBg
                                } else {
                                    PromptShiftColors.CarbonElevated
                                },
                            )
                            .border(
                                1.dp,
                                if (batchState.isCurrentCopied) {
                                    PromptShiftColors.CopiedEmerald.copy(alpha = 0.6f)
                                } else {
                                    PromptShiftColors.MetalBorder
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = if (batchState.isCurrentCopied) {
                                "Slot #${batchState.displayIndex}/${batchState.totalCount} ✅"
                            } else {
                                "Slot #${batchState.displayIndex}/${batchState.totalCount}"
                            },
                            color = if (batchState.isCurrentCopied) {
                                PromptShiftColors.CopiedEmerald
                            } else {
                                PromptShiftColors.TextMuted
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                MiniActionChip(
                    label = "+Snippet",
                    onClick = onSaveCurrentSlotAsSnippet,
                )
            }

            batchState.effectiveCurrentPrompt(activeTriggerWord)?.let { preview ->
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PromptShiftColors.ObsidianBg)
                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = preview,
                        color = PromptShiftColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onLoadFromClipboard,
                modifier = Modifier
                    .weight(1.35f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PromptShiftColors.CarbonElevated,
                    contentColor = PromptShiftColors.TextPrimary,
                ),
                border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
            ) {
                Text(
                    text = "Load from Clipboard",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            OutlinedButton(
                onClick = onClearClick,
                enabled = !batchState.isEmpty && !isStreaming,
                modifier = Modifier
                    .weight(0.95f)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (awaitingClearConfirm) PromptShiftColors.GtCrimson else Color.Transparent,
                    contentColor = if (awaitingClearConfirm) PromptShiftColors.TextPrimary else PromptShiftColors.TextMuted,
                    disabledContentColor = PromptShiftColors.TextMuted.copy(alpha = 0.35f),
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = when {
                        batchState.isEmpty || isStreaming -> PromptShiftColors.MetalBorder.copy(alpha = 0.45f)
                        awaitingClearConfirm -> PromptShiftColors.GtCrimsonGlow
                        else -> PromptShiftColors.MetalBorder
                    },
                ),
            ) {
                Text(
                    text = if (awaitingClearConfirm) "Confirm?" else "Clear Batch",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AiFormatHelperCard(
    onCopyAiInstruction: () -> Unit,
) {
    CarbonCard {
        Text(
            text = "AI FORMAT HELPER",
            color = PromptShiftColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Append this rule to any AI prompt so it returns all prompts in one code block separated by ===PROMPT=== on its own line.",
            color = PromptShiftColors.TextPrimary.copy(alpha = 0.86f),
            fontSize = 13.sp,
            lineHeight = 18.5.sp,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PromptShiftColors.ObsidianBg)
                .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = PromptFormatGuide.SAMPLE_BLOCK,
                color = PromptShiftColors.TextPrimary.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCopyAiInstruction,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = PromptShiftColors.CarbonElevated,
                contentColor = PromptShiftColors.TextPrimary,
            ),
            border = BorderStroke(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.75f)),
        ) {
            Text(
                text = "Copy AI Instruction",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }
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
