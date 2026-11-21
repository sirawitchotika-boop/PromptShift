package com.promptshift.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.ime.PromptShiftImeService
import com.promptshift.app.timer.TimerState
import com.promptshift.app.ui.theme.PromptShiftColors
import com.promptshift.app.ui.theme.PromptShiftTheme

/**
 * Simple translucent Countdown Timer popup launched from the Floating Bubble or Mini-Keyboard [⏱] chip.
 */
class TimerPopupActivity : ComponentActivity() {

    private var hasTriggeredKeyboardReturn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = PromptShiftApp.from(this)
        val timerManager = app.timerManager
        val aiConfigRepository = app.aiConfigRepository

        setContent {
            PromptShiftTheme {
                val timerState by timerManager.state.collectAsStateWithLifecycle()
                val aiConfig by aiConfigRepository.configState.collectAsStateWithLifecycle()

                TimerPopupContent(
                    timerState = timerState,
                    presetsSeconds = aiConfig.timerPresetsSeconds,
                    onStart = { min, sec ->
                        if (timerManager.start(min, sec)) {
                            finish()
                        }
                    },
                    onStartPreset = { totalSeconds ->
                        if (timerManager.startTotalSeconds(totalSeconds)) {
                            finish()
                        }
                    },
                    onSavePreset = { min, sec ->
                        aiConfigRepository.addTimerPreset(min, sec)
                    },
                    onRemovePreset = { totalSeconds ->
                        aiConfigRepository.removeTimerPreset(totalSeconds)
                    },
                    onCancel = { timerManager.cancel() },
                    onReset = { timerManager.reset() },
                    onDismissAlert = { timerManager.dismissFinishedAlert() },
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun finish() {
        if (!hasTriggeredKeyboardReturn) {
            hasTriggeredKeyboardReturn = true
            val preferredImeId = PromptShiftApp.from(this)
                .aiConfigRepository
                .configState
                .value
                .preferredReturnImeId
            PromptShiftImeService.switchKeyboardAfterTimerPopup(this, preferredImeId)
        }
        super.finish()
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, TimerPopupActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }
            context.startActivity(intent)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerPopupContent(
    timerState: TimerState,
    presetsSeconds: List<Int>,
    onStart: (Int, Int) -> Unit,
    onStartPreset: (Int) -> Unit,
    onSavePreset: (Int, Int) -> Boolean,
    onRemovePreset: (Int) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onDismissAlert: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutesInput by remember { mutableStateOf("") }
    var secondsInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var isEditingPresets by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = RoundedCornerShape(20.dp),
            color = PromptShiftColors.CarbonSurface.copy(alpha = 0.96f),
            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "COUNTDOWN TIMER",
                        color = PromptShiftColors.TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp,
                    )
                    Text(
                        text = "✕",
                        color = PromptShiftColors.TextMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (timerState.isRunning) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PromptShiftColors.ObsidianBg)
                            .border(1.dp, PromptShiftColors.CopiedEmerald.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = timerState.formattedRemaining(),
                            color = PromptShiftColors.CopiedEmerald,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PromptShiftColors.QuotaWarnRed.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.QuotaWarnRed),
                        ) {
                            Text("Cancel", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = onReset,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PromptShiftColors.CarbonElevated,
                                contentColor = PromptShiftColors.TextPrimary,
                            ),
                            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                        ) {
                            Text("Reset", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    if (timerState.isFinished) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(PromptShiftColors.CopiedEmeraldBg)
                                .border(1.dp, PromptShiftColors.CopiedEmerald, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Timer Done! ⏱",
                                color = PromptShiftColors.CopiedEmerald,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Dismiss",
                                color = PromptShiftColors.TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PromptShiftColors.CarbonElevated)
                                    .clickable(onClick = onDismissAlert)
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }

                    // Quick 1-tap Presets section
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
                                text = if (isEditingPresets) "TAP PRESET TO REMOVE" else "QUICK PRESETS (1-TAP START)",
                                color = if (isEditingPresets) PromptShiftColors.QuotaWarnRed else PromptShiftColors.TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
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
                                text = "No saved presets yet — enter Min/Sec below and tap + Save.",
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
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = if (isEditingPresets) "$label  ✕" else label,
                                            color = if (isEditingPresets) PromptShiftColors.QuotaWarnRed else PromptShiftColors.TextPrimary,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = minutesInput,
                            onValueChange = { raw ->
                                minutesInput = raw.filter { it.isDigit() }.take(3)
                                statusMessage = null
                            },
                            label = { Text("Min", fontSize = 12.sp) },
                            placeholder = { Text("0", fontSize = 13.sp) },
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
                                statusMessage = null
                            },
                            label = { Text("Sec", fontSize = 12.sp) },
                            placeholder = { Text("00", fontSize = 13.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = darkInputColors(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    if (!statusMessage.isNullOrBlank()) {
                        Text(
                            text = statusMessage.orEmpty(),
                            color = if (statusIsError) PromptShiftColors.QuotaWarnRed else PromptShiftColors.CopiedEmerald,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                val min = minutesInput.toIntOrNull() ?: 0
                                val sec = secondsInput.toIntOrNull() ?: 0
                                if (min == 0 && sec == 0) {
                                    statusMessage = "Enter minutes or seconds"
                                    statusIsError = true
                                } else if (onSavePreset(min, sec)) {
                                    val label = TimerState.formatPresetLabel(min.coerceIn(0, 999) * 60 + sec.coerceIn(0, 59))
                                    statusMessage = "Saved preset $label"
                                    statusIsError = false
                                } else {
                                    statusMessage = "Preset already exists"
                                    statusIsError = true
                                }
                            },
                            modifier = Modifier
                                .weight(0.42f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = PromptShiftColors.CarbonElevated,
                                contentColor = PromptShiftColors.TextPrimary,
                            ),
                        ) {
                            Text("+ Save", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val min = minutesInput.toIntOrNull() ?: 0
                                val sec = secondsInput.toIntOrNull() ?: 0
                                if (min == 0 && sec == 0) {
                                    statusMessage = "Enter minutes or seconds"
                                    statusIsError = true
                                } else {
                                    onStart(min, sec)
                                }
                            },
                            modifier = Modifier
                                .weight(0.58f)
                                .height(46.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PromptShiftColors.GtCrimson,
                                contentColor = PromptShiftColors.TextPrimary,
                            ),
                            border = BorderStroke(1.dp, PromptShiftColors.GtCrimsonGlow),
                        ) {
                            Text("Start Timer", fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
