package com.promptshift.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.timer.TimerState
import com.promptshift.app.ui.theme.PromptShiftColors

@Composable
internal fun TriggerWordCard(
    aiConfig: AiConfigState,
    onSetActiveTriggerWord: (String?, Boolean?) -> Unit,
    onToggleTriggerEnabled: (Boolean) -> Unit,
    onToggleTriggerChip: (String) -> Unit,
    onSaveTriggerChip: (String) -> Unit,
    onRemoveTriggerChip: (String) -> Unit,
) {
    var input by remember(aiConfig.activeTriggerWord) {
        mutableStateOf(aiConfig.activeTriggerWord.orEmpty())
    }

    CarbonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TRIGGER WORD (PINNED)",
                    color = PromptShiftColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = if (aiConfig.effectiveTriggerWord != null) {
                        "Live prepend: \"${aiConfig.effectiveTriggerWord}, <prompt>\""
                    } else {
                        "Off — tap a chip or toggle ON to prepend before every Copy & Insert"
                    },
                    color = if (aiConfig.effectiveTriggerWord != null) {
                        PromptShiftColors.CopiedEmerald
                    } else {
                        PromptShiftColors.TextMuted
                    },
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = aiConfig.triggerWordEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && input.isNotBlank()) {
                        onSetActiveTriggerWord(input, true)
                    } else {
                        onToggleTriggerEnabled(enabled)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PromptShiftColors.TextPrimary,
                    checkedTrackColor = PromptShiftColors.CopiedEmerald,
                ),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { next ->
                    input = next
                    onSetActiveTriggerWord(next, if (next.isBlank()) false else null)
                },
                placeholder = { Text("Type or paste trigger word (e.g. ohwx woman)", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = darkInputColors(),
                shape = RoundedCornerShape(12.dp),
            )
            OutlinedButton(
                onClick = {
                    val clean = input.trim()
                    if (clean.isNotEmpty()) {
                        onSaveTriggerChip(clean)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.75f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = PromptShiftColors.CarbonElevated,
                    contentColor = PromptShiftColors.TextPrimary,
                ),
            ) {
                Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (aiConfig.triggerWords.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                aiConfig.triggerWords.forEach { chip ->
                    val isSelected = aiConfig.triggerWordEnabled && aiConfig.activeTriggerWord == chip
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PromptShiftColors.GtCrimson else PromptShiftColors.CarbonElevated)
                            .border(
                                1.dp,
                                if (isSelected) PromptShiftColors.GtCrimsonGlow else PromptShiftColors.MetalBorder,
                                RoundedCornerShape(10.dp),
                            )
                            .clickable { onToggleTriggerChip(chip) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = chip,
                            color = if (isSelected) PromptShiftColors.TextPrimary else PromptShiftColors.TextMuted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        Text(
                            text = "×",
                            color = if (isSelected) PromptShiftColors.TextPrimary else PromptShiftColors.QuotaWarnRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onRemoveTriggerChip(chip) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SnippetsAndRecentCard(
    aiConfig: AiConfigState,
    onAddSnippet: () -> Unit,
    onEditSnippet: (PromptSnippet) -> Unit,
    onDeleteSnippet: (String) -> Unit,
    onCopySnippet: (PromptSnippet) -> Unit,
    onCopyRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Saved Snippets, 1 = Recent 10

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
                title = "Saved Snippets (${aiConfig.snippets.size})",
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
            )
            SegmentTabButton(
                modifier = Modifier.weight(1f),
                title = "Recent 10 (${aiConfig.recentPrompts.size})",
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedTab == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (aiConfig.snippets.isEmpty()) {
                    Text(
                        text = "Save reusable style tags or full prompts here for 1-tap auto-type on the Mini-Keyboard and Floating Bubble.",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    aiConfig.snippets.forEach { snippet ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PromptShiftColors.ObsidianBg)
                                .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = snippet.title,
                                    color = PromptShiftColors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (snippet.includeTriggerWord) "TRIGGER ON" else "TAG ONLY",
                                    color = if (snippet.includeTriggerWord) {
                                        PromptShiftColors.CopiedEmerald
                                    } else {
                                        PromptShiftColors.TextMuted
                                    },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                text = snippet.body,
                                color = PromptShiftColors.TextMuted,
                                fontSize = 11.5.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniActionChip(
                                    label = "Copy",
                                    onClick = { onCopySnippet(snippet) },
                                )
                                MiniActionChip(
                                    label = "Edit",
                                    onClick = { onEditSnippet(snippet) },
                                )
                                MiniActionChip(
                                    label = "Delete",
                                    isDanger = true,
                                    onClick = { onDeleteSnippet(snippet.id) },
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onAddSnippet,
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
                    Text("+ Add Saved Snippet", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (aiConfig.recentPrompts.isEmpty()) {
                    Text(
                        text = "The last 10 prompts you copy or insert are remembered here automatically (deduplicated).",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                    )
                } else {
                    aiConfig.recentPrompts.forEachIndexed { idx, recent ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PromptShiftColors.ObsidianBg)
                                .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                                .clickable { onCopyRecent(recent) }
                                .padding(11.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "RECENT #${idx + 1} • ${recent.length} chars (tap to copy)",
                                color = PromptShiftColors.GtCrimsonGlow,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = recent,
                                color = PromptShiftColors.TextPrimary.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onClearRecent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextMuted),
                    ) {
                        Text("Clear Recent 10", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CountdownTimerCard(
    timerState: TimerState,
    presetsSeconds: List<Int>,
    timerPillEnabled: Boolean,
    onToggleTimerPill: (Boolean) -> Unit,
    onStartTimer: (minutes: Int, seconds: Int) -> Unit,
    onStartPreset: (totalSeconds: Int) -> Unit,
    onSavePreset: (minutes: Int, seconds: Int) -> Boolean,
    onRemovePreset: (totalSeconds: Int) -> Unit,
    onCancelTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onDismissAlert: () -> Unit,
) {
    var minutesInput by remember { mutableStateOf("") }
    var secondsInput by remember { mutableStateOf("") }
    var inlineMessage by remember { mutableStateOf<String?>(null) }
    var inlineIsError by remember { mutableStateOf(false) }

    CarbonCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "COUNTDOWN TIMER",
                color = PromptShiftColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = when {
                    timerState.isRunning -> "RUNNING • ${timerState.formattedRemaining()}"
                    timerState.isFinished -> "DONE ⏱"
                    timerPillEnabled -> "Floating Pill + Alert"
                    else -> "Notification + Alert"
                },
                color = when {
                    timerState.isRunning -> PromptShiftColors.CopiedEmerald
                    timerState.isFinished -> PromptShiftColors.GtCrimsonGlow
                    else -> PromptShiftColors.TextMuted
                },
                fontFamily = if (timerState.isRunning) FontFamily.Monospace else FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (timerState.isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PromptShiftColors.ObsidianBg)
                    .border(1.dp, PromptShiftColors.CopiedEmerald.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = timerState.formattedRemaining(),
                    color = PromptShiftColors.CopiedEmerald,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onCancelTimer,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PromptShiftColors.QuotaWarnRed.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.QuotaWarnRed),
                ) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onResetTimer,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PromptShiftColors.CarbonElevated,
                        contentColor = PromptShiftColors.TextPrimary,
                    ),
                ) {
                    Text("Reset", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            if (timerState.isFinished) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PromptShiftColors.CopiedEmeraldBg)
                        .border(1.dp, PromptShiftColors.CopiedEmerald, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PromptShift Timer Done ⏱",
                        color = PromptShiftColors.CopiedEmerald,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    MiniActionChip(
                        label = "Dismiss",
                        onClick = onDismissAlert,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            TimerPresetsSection(
                presetsSeconds = presetsSeconds,
                onStartPreset = onStartPreset,
                onRemovePreset = onRemovePreset,
                headerTitle = "PRESETS (1-TAP START)",
                emptyText = "No presets saved yet — enter Min/Sec below and tap + Save.",
                compact = true,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = minutesInput,
                    onValueChange = { raw ->
                        minutesInput = raw.filter { it.isDigit() }.take(3)
                        inlineMessage = null
                    },
                    label = { Text("Min", fontSize = 11.5.sp) },
                    placeholder = { Text("0", fontSize = 12.5.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = secondsInput,
                    onValueChange = { raw ->
                        secondsInput = raw.filter { it.isDigit() }.take(2)
                        inlineMessage = null
                    },
                    label = { Text("Sec", fontSize = 11.5.sp) },
                    placeholder = { Text("00", fontSize = 12.5.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = darkInputColors(),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val min = minutesInput.toIntOrNull() ?: 0
                        val sec = secondsInput.toIntOrNull() ?: 0
                        if (min == 0 && sec == 0) {
                            inlineMessage = "Enter min or sec"
                            inlineIsError = true
                        } else if (onSavePreset(min, sec)) {
                            val label = TimerState.formatPresetLabel(min.coerceIn(0, 999) * 60 + sec.coerceIn(0, 59))
                            inlineMessage = "Saved preset $label"
                            inlineIsError = false
                        } else {
                            inlineMessage = "Preset already exists"
                            inlineIsError = true
                        }
                    },
                    modifier = Modifier
                        .weight(0.42f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PromptShiftColors.CarbonElevated,
                        contentColor = PromptShiftColors.TextPrimary,
                    ),
                ) {
                    Text("+ Save", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val min = minutesInput.toIntOrNull() ?: 0
                        val sec = secondsInput.toIntOrNull() ?: 0
                        if (min == 0 && sec == 0) {
                            inlineMessage = "Enter min or sec"
                            inlineIsError = true
                        } else {
                            onStartTimer(min, sec)
                        }
                    },
                    modifier = Modifier
                        .weight(0.58f)
                        .height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PromptShiftColors.GtCrimson,
                        contentColor = PromptShiftColors.TextPrimary,
                    ),
                ) {
                    Text("Start Timer", fontWeight = FontWeight.Bold)
                }
            }

            if (!inlineMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = inlineMessage.orEmpty(),
                    color = if (inlineIsError) PromptShiftColors.QuotaWarnRed else PromptShiftColors.CopiedEmerald,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(PromptShiftColors.ObsidianBg)
                .border(1.dp, PromptShiftColors.MetalBorder.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Floating Countdown Pill",
                    color = PromptShiftColors.TextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Top-center & draggable (off = notification only)",
                    color = PromptShiftColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
            Switch(
                checked = timerPillEnabled,
                onCheckedChange = onToggleTimerPill,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PromptShiftColors.TextPrimary,
                    checkedTrackColor = PromptShiftColors.CopiedEmerald,
                    uncheckedThumbColor = PromptShiftColors.TextMuted,
                    uncheckedTrackColor = PromptShiftColors.CarbonElevated,
                ),
            )
        }
    }
}
