package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.domain.ReasoningEffort
import com.promptshift.app.domain.StartGenerationResult
import com.promptshift.app.ui.theme.PromptShiftColors

@Composable
internal fun DirectAiGenerateCard(
    aiConfig: AiConfigState,
    generationState: GenerationState,
    onSelectProvider: (String) -> Unit,
    onSelectProviderModel: (providerId: String, modelId: String) -> Unit,
    onSelectInstruction: (String?) -> Unit,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    onClearRecentDirections: () -> Unit,
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

                if (aiConfig.recentDirections.isNotEmpty()) {
                    RecentDirectionsHistoryBox(
                        recentDirections = aiConfig.recentDirections,
                        currentDraft = direction,
                        onSelectDirection = { chosen ->
                            direction = chosen
                            inlineError = null
                        },
                        onClear = onClearRecentDirections,
                    )
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
