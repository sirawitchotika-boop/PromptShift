package com.promptshift.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.timer.TimerState
import com.promptshift.app.ui.theme.PromptShiftColors

/**
 * Shared 1-tap editable countdown presets section used by both [TimerPopupActivity]
 * and the Hub's [CountdownTimerCard].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimerPresetsSection(
    presetsSeconds: List<Int>,
    onStartPreset: (Int) -> Unit,
    onRemovePreset: (Int) -> Unit,
    headerTitle: String = "PRESETS (1-TAP START)",
    emptyText: String = "No presets saved yet — enter Min/Sec below and tap + Save.",
    compact: Boolean = false,
) {
    var isEditingPresets by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isEditingPresets) "TAP PRESET TO REMOVE" else headerTitle,
                color = if (isEditingPresets) PromptShiftColors.QuotaWarnRed else PromptShiftColors.TextMuted,
                fontSize = if (compact) 10.5.sp else 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = if (compact) 0.7.sp else 0.8.sp,
            )
            if (presetsSeconds.isNotEmpty()) {
                Text(
                    text = if (isEditingPresets) "Done" else "Edit",
                    color = if (isEditingPresets) PromptShiftColors.CopiedEmerald else PromptShiftColors.TextMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(PromptShiftColors.CarbonElevated)
                        .clickable { isEditingPresets = !isEditingPresets }
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                )
            }
        }

        if (presetsSeconds.isEmpty()) {
            Text(
                text = emptyText,
                color = PromptShiftColors.TextMuted,
                fontSize = 11.5.sp,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presetsSeconds.forEach { totalSec ->
                    val label = TimerState.formatPresetLabel(totalSec)
                    val borderColor = if (isEditingPresets) {
                        PromptShiftColors.QuotaWarnRed.copy(alpha = 0.75f)
                    } else {
                        PromptShiftColors.MetalBorder
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PromptShiftColors.CarbonElevated)
                            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable {
                                if (isEditingPresets) {
                                    onRemovePreset(totalSec)
                                    if (presetsSeconds.size <= 1) {
                                        isEditingPresets = false
                                    }
                                } else {
                                    onStartPreset(totalSec)
                                }
                            }
                            .padding(
                                horizontal = if (compact) 11.dp else 12.dp,
                                vertical = if (compact) 7.dp else 8.dp,
                            ),
                    ) {
                        Text(
                            text = if (isEditingPresets) "$label  ✕" else label,
                            color = if (isEditingPresets) PromptShiftColors.QuotaWarnRed else PromptShiftColors.TextPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (compact) 12.5.sp else 13.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
