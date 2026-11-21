package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.ui.theme.PromptShiftColors

@Composable
internal fun HeaderWordmark(
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
internal fun FeedbackBanner(message: String, isError: Boolean) {
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
internal fun HeroControlCard(
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
internal fun RealmeReadinessStrip(
    readiness: RealmeReadinessState,
    preferredReturnKeyboardLabel: String,
    hasPreferredReturnKeyboard: Boolean,
    onOpenOverlaySettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenKeyboardSettings: () -> Unit,
    onSwitchKeyboard: () -> Unit,
    onPickReturnKeyboard: () -> Unit,
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
        ReadinessChip(
            modifier = Modifier.fillMaxWidth(),
            title = "Return Keyboard (after Timer popup)",
            statusText = "$preferredReturnKeyboardLabel →",
            isReady = hasPreferredReturnKeyboard,
            accentWhenPending = PromptShiftColors.TextMuted,
            onClick = onPickReturnKeyboard,
        )
    }
}

@Composable
internal fun ReturnKeyboardPickerDialog(
    selectedImeId: String?,
    enabledKeyboards: List<EnabledKeyboardOption>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PromptShiftColors.CarbonSurface,
        titleContentColor = PromptShiftColors.TextPrimary,
        textContentColor = PromptShiftColors.TextMuted,
        title = {
            Text(
                text = "Return Keyboard",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "When the Timer popup closes while PromptShift Keyboard is active, switch back to:",
                    color = PromptShiftColors.TextMuted,
                    fontSize = 12.sp,
                )

                ReturnKeyboardOptionRow(
                    label = "Auto (Previous Keyboard)",
                    subtitle = "Safe default if your keyboard changes",
                    selected = selectedImeId.isNullOrBlank(),
                    onClick = { onSelect(null) },
                )

                enabledKeyboards.forEach { option ->
                    ReturnKeyboardOptionRow(
                        label = option.label,
                        subtitle = null,
                        selected = selectedImeId == option.id,
                        onClick = { onSelect(option.id) },
                    )
                }

                if (enabledKeyboards.isEmpty()) {
                    Text(
                        text = "No other enabled keyboards detected. Auto fallback will be used.",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 11.5.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PromptShiftColors.TextMuted)
            }
        },
    )
}

@Composable
private fun ReturnKeyboardOptionRow(
    label: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) {
        PromptShiftColors.CopiedEmerald.copy(alpha = 0.75f)
    } else {
        PromptShiftColors.MetalBorder
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PromptShiftColors.CopiedEmeraldBg else PromptShiftColors.CarbonElevated)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (selected) PromptShiftColors.CopiedEmerald else PromptShiftColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = PromptShiftColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }
        if (selected) {
            Text(
                text = "✓",
                color = PromptShiftColors.CopiedEmerald,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
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
