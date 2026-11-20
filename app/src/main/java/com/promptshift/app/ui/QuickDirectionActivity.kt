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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.domain.ReasoningEffort
import com.promptshift.app.domain.StartGenerationResult
import com.promptshift.app.ui.theme.PromptShiftColors
import com.promptshift.app.ui.theme.PromptShiftTheme

/**
 * Compact Quick-Direction popup launched by tapping [AI] on the Floating Bubble.
 * Floats cleanly over the user's current app and dismisses immediately once SSE streaming starts.
 */
class QuickDirectionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = PromptShiftApp.from(this)
        val configRepo = app.aiConfigRepository
        val coordinator = app.generationCoordinator

        setContent {
            PromptShiftTheme {
                val config by configRepo.configState.collectAsStateWithLifecycle()
                val genState by coordinator.generationState.collectAsStateWithLifecycle()

                QuickDirectionPopupContent(
                    config = config,
                    generationState = genState,
                    onSelectProvider = { configRepo.selectProvider(it) },
                    onSelectModel = { providerId, modelId ->
                        configRepo.selectProviderModel(providerId, modelId)
                    },
                    onSelectInstruction = { configRepo.selectInstruction(it) },
                    onSelectReasoningEffort = { configRepo.setReasoningEffort(it) },
                    onToggleTriggerWordChip = { configRepo.toggleTriggerWordChip(it) },
                    onSetTriggerWordEnabled = { configRepo.setTriggerWordEnabled(it) },
                    onStartGeneration = { direction, count, allowReplace ->
                        coordinator.startGeneration(
                            direction = direction,
                            promptCount = count,
                            allowReplaceExisting = allowReplace,
                        )
                    },
                    onCancelStream = { coordinator.cancelGeneration() },
                    onOpenHub = {
                        val intent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, QuickDirectionActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun QuickDirectionPopupContent(
    config: AiConfigState,
    generationState: GenerationState,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (providerId: String, modelId: String) -> Unit,
    onSelectInstruction: (String?) -> Unit,
    onSelectReasoningEffort: (ReasoningEffort) -> Unit,
    onToggleTriggerWordChip: (String) -> Unit,
    onSetTriggerWordEnabled: (Boolean) -> Unit,
    onStartGeneration: (String, Int, Boolean) -> StartGenerationResult,
    onCancelStream: () -> Unit,
    onOpenHub: () -> Unit,
    onDismiss: () -> Unit,
) {
    var direction by remember { mutableStateOf("") }
    var selectedCount by remember(config.defaultPromptCount) {
        mutableIntStateOf(config.defaultPromptCount)
    }
    var customCountMode by remember {
        mutableStateOf(config.defaultPromptCount !in listOf(5, 10, 20))
    }
    var customCountInput by remember(config.defaultPromptCount) {
        mutableStateOf(config.defaultPromptCount.toString())
    }
    var pendingOverwriteCount by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val effectiveCount = if (customCountMode) {
        customCountInput.toIntOrNull()?.coerceIn(1, 100) ?: selectedCount
    } else {
        selectedCount
    }

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
            .padding(horizontal = 16.dp, vertical = 20.dp),
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
            color = PromptShiftColors.CarbonSurface,
            border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "QUICK DIRECTION",
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

                // Provider selector
                if (config.providers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PromptShiftColors.ObsidianBg)
                            .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "No AI provider configured yet.",
                                color = PromptShiftColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Add an OpenAI-compatible provider (URL, API Key, Model) in the Main Hub first.",
                                color = PromptShiftColors.TextMuted,
                                fontSize = 12.sp,
                            )
                            OutlinedButton(
                                onClick = onOpenHub,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PromptShiftColors.GtCrimson),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = PromptShiftColors.TextPrimary,
                                ),
                            ) {
                                Text("Open Hub to Add Provider", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    CompactSelectorSection(label = "PROVIDER") {
                        config.providers.forEach { provider ->
                            val selected = provider.id == config.activeProvider?.id
                            SelectablePillChip(
                                label = provider.name,
                                selected = selected,
                                onClick = { onSelectProvider(provider.id) },
                            )
                        }
                    }

                    // Favorite AI Models 1-tap switcher
                    val activeProvider = config.activeProvider
                    if (activeProvider != null && activeProvider.allSelectableModels.isNotEmpty()) {
                        CompactSelectorSection(label = "MODEL") {
                            activeProvider.allSelectableModels.forEach { model ->
                                SelectablePillChip(
                                    label = model,
                                    selected = model == activeProvider.modelId,
                                    onClick = { onSelectModel(activeProvider.id, model) },
                                )
                            }
                        }
                    }

                    // Instruction profile selector (None + saved presets)
                    CompactSelectorSection(label = "INSTRUCTION") {
                        SelectablePillChip(
                            label = "None",
                            selected = config.activeInstructionId == null,
                            onClick = { onSelectInstruction(null) },
                        )
                        config.instructions.forEach { inst ->
                            SelectablePillChip(
                                label = inst.name,
                                selected = inst.id == config.activeInstructionId,
                                onClick = { onSelectInstruction(inst.id) },
                            )
                        }
                    }

                    // Reasoning Effort selector
                    CompactSelectorSection(label = "REASONING EFFORT") {
                        ReasoningEffort.entries.forEach { effort ->
                            SelectablePillChip(
                                label = effort.label,
                                selected = config.reasoningEffort == effort,
                                onClick = { onSelectReasoningEffort(effort) },
                            )
                        }
                    }

                    // Trigger Word quick toggle & chips
                    if (!config.activeTriggerWord.isNullOrBlank() || config.triggerWords.isNotEmpty()) {
                        CompactSelectorSection(label = "TRIGGER WORD") {
                            SelectablePillChip(
                                label = "Off",
                                selected = !config.triggerWordEnabled,
                                onClick = { onSetTriggerWordEnabled(false) },
                            )
                            val chips = (listOfNotNull(config.activeTriggerWord) + config.triggerWords).distinct()
                            chips.forEach { tw ->
                                SelectablePillChip(
                                    label = tw,
                                    selected = config.triggerWordEnabled && config.activeTriggerWord == tw,
                                    onClick = { onToggleTriggerWordChip(tw) },
                                )
                            }
                        }
                    }

                    // Prompt count selector
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "PROMPT COUNT",
                            color = PromptShiftColors.TextMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.9.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            listOf(5, 10, 20).forEach { count ->
                                SelectablePillChip(
                                    label = count.toString(),
                                    selected = !customCountMode && selectedCount == count,
                                    onClick = {
                                        customCountMode = false
                                        selectedCount = count
                                        customCountInput = count.toString()
                                    },
                                )
                            }
                            SelectablePillChip(
                                label = "Custom",
                                selected = customCountMode,
                                onClick = { customCountMode = true },
                            )
                            if (customCountMode) {
                                OutlinedTextField(
                                    value = customCountInput,
                                    onValueChange = { raw ->
                                        val digits = raw.filter { it.isDigit() }.take(3)
                                        customCountInput = digits
                                    },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(74.dp),
                                    colors = darkInputColors(),
                                    shape = RoundedCornerShape(10.dp),
                                )
                            }
                        }
                    }

                    // Direction box
                    OutlinedTextField(
                        value = direction,
                        onValueChange = {
                            direction = it
                            errorMessage = null
                        },
                        label = { Text("Direction / theme", fontSize = 12.sp) },
                        placeholder = {
                            Text(
                                "e.g. neon rainy Tokyo street, cinematic wide shots",
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

                    // Quick-append Snippets into Direction box
                    if (config.snippets.isNotEmpty()) {
                        CompactSelectorSection(label = "TAP SNIPPET TO APPEND") {
                            config.snippets.forEach { snippet ->
                                SelectablePillChip(
                                    label = "+ ${snippet.title}",
                                    selected = false,
                                    onClick = {
                                        direction = PromptTextComposer.appendToDraft(direction, snippet.body)
                                        errorMessage = null
                                    },
                                )
                            }
                        }
                    }

                    // Error banner
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage.orEmpty(),
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
                                                StartGenerationResult.Started -> onDismiss()
                                                is StartGenerationResult.Blocked -> errorMessage = res.reason
                                                is StartGenerationResult.NeedsOverwriteConfirmation -> Unit
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PromptShiftColors.GtCrimson,
                                            contentColor = PromptShiftColors.TextPrimary,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    ) {
                                        Text("Replace & Stream", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { pendingOverwriteCount = null },
                                        modifier = Modifier.weight(0.8f),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = PromptShiftColors.TextMuted,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    ) {
                                        Text("Keep", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Action CTA
                    if (generationState is GenerationState.Streaming) {
                        Button(
                            onClick = onCancelStream,
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
                            Text(
                                text = "Cancel Stream (${generationState.unlockedCount}/${generationState.targetCount})",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                errorMessage = null
                                when (val result = onStartGeneration(direction, effectiveCount, false)) {
                                    StartGenerationResult.Started -> onDismiss()
                                    is StartGenerationResult.NeedsOverwriteConfirmation -> {
                                        pendingOverwriteCount = result.uncopiedCount
                                    }
                                    is StartGenerationResult.Blocked -> {
                                        errorMessage = result.reason
                                    }
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
}

@Composable
internal fun CompactSelectorSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            color = PromptShiftColors.TextMuted,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
internal fun SelectablePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) PromptShiftColors.GtCrimson else PromptShiftColors.CarbonElevated
    val border = if (selected) PromptShiftColors.GtCrimsonGlow else PromptShiftColors.MetalBorder
    val fg = if (selected) PromptShiftColors.TextPrimary else PromptShiftColors.TextMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun darkInputColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = PromptShiftColors.TextPrimary,
    unfocusedTextColor = PromptShiftColors.TextPrimary,
    focusedContainerColor = PromptShiftColors.ObsidianBg,
    unfocusedContainerColor = PromptShiftColors.ObsidianBg,
    focusedBorderColor = PromptShiftColors.GtCrimson,
    unfocusedBorderColor = PromptShiftColors.MetalBorder,
    focusedLabelColor = PromptShiftColors.TextPrimary,
    unfocusedLabelColor = PromptShiftColors.TextMuted,
    cursorColor = PromptShiftColors.GtCrimson,
)
