package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.ui.theme.PromptShiftColors

@Composable
internal fun ActiveBatchCard(
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
internal fun AiFormatHelperCard(
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
