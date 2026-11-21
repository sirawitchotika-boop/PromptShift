package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.ui.theme.PromptShiftColors

@Composable
internal fun ProfilesManagerCard(
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
