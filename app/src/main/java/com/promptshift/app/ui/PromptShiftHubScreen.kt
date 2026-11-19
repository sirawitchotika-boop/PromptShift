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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.ui.theme.PromptShiftColors
import kotlinx.coroutines.delay

@Composable
fun PromptShiftHubScreen(
    batchState: PromptBatchState,
    isOverlayRunning: Boolean,
    readiness: RealmeReadinessState,
    statusBanner: String?,
    statusBannerIsError: Boolean,
    onToggleBubble: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onLoadFromClipboard: () -> Unit,
    onClearBatch: () -> Unit,
    onCopyAiInstruction: () -> Unit,
) {
    var awaitingClearConfirm by remember { mutableStateOf(false) }

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
            HeaderWordmark(isOverlayRunning = isOverlayRunning)

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
            )

            // 4. Active Batch Card
            ActiveBatchCard(
                batchState = batchState,
                awaitingClearConfirm = awaitingClearConfirm,
                onLoadFromClipboard = onLoadFromClipboard,
                onClearClick = {
                    if (awaitingClearConfirm) {
                        awaitingClearConfirm = false
                        onClearBatch()
                    } else {
                        awaitingClearConfirm = true
                    }
                },
            )

            // 5. NanoGPT Format Helper Card
            NanoGptFormatHelperCard(
                onCopyAiInstruction = onCopyAiInstruction,
            )

            // Subtle safety footer
            Text(
                text = "Copy-only clipboard helper • Banking-app safe",
                color = PromptShiftColors.TextMuted.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun HeaderWordmark(isOverlayRunning: Boolean) {
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

        val dotColor = if (isOverlayRunning) PromptShiftColors.CopiedEmerald else PromptShiftColors.TextMuted
        val pillText = if (isOverlayRunning) "ACTIVE" else "STANDBY"
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
                color = if (isOverlayRunning) PromptShiftColors.CopiedEmerald else PromptShiftColors.TextMuted,
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
                isOverlayRunning -> "Bubble is floating over your apps. Tap ✕ on the pill or Stop below anytime."
                canDrawOverlays -> "Launch the floating pill before opening SeaArt so you can Load and Copy prompts in one tap."
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
private fun ActiveBatchCard(
    batchState: PromptBatchState,
    awaitingClearConfirm: Boolean,
    onLoadFromClipboard: () -> Unit,
    onClearClick: () -> Unit,
) {
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

            val countColor = if (batchState.isCurrentExceedingQuota) {
                PromptShiftColors.QuotaWarnRed
            } else {
                PromptShiftColors.TextMuted
            }
            Text(
                text = batchState.charCountLabel(),
                color = countColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (batchState.isCurrentExceedingQuota) FontWeight.Bold else FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (batchState.isEmpty) {
            Text(
                text = "No batch loaded yet",
                color = PromptShiftColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Copy a ===PROMPT=== batch from NanoGPT, then tap Load on the bubble or below.",
                color = PromptShiftColors.TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        } else {
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

            batchState.currentPrompt?.let { preview ->
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
                enabled = !batchState.isEmpty,
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
                        batchState.isEmpty -> PromptShiftColors.MetalBorder.copy(alpha = 0.45f)
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
private fun NanoGptFormatHelperCard(
    onCopyAiInstruction: () -> Unit,
) {
    CarbonCard {
        Text(
            text = "NANOGPT FORMAT HELPER",
            color = PromptShiftColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Ask NanoGPT to wrap all positive prompts inside a single code block separated by ===PROMPT=== on its own line.",
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
private fun CarbonCard(
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
