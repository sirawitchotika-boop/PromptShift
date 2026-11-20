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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.ui.theme.PromptShiftColors
import com.promptshift.app.ui.theme.PromptShiftTheme

/**
 * Translucent Snippets & Recent 10 popup launched from the Floating Bubble's [📌] button.
 * Supports 1-tap Copy (respecting per-snippet Trigger Word toggle) and 1-tap Save current slot.
 */
class SnippetsPopupActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = PromptShiftApp.from(this)
        val configRepo = app.aiConfigRepository
        val batchRepo = app.repository

        setContent {
            PromptShiftTheme {
                val config by configRepo.configState.collectAsStateWithLifecycle()
                val batch by batchRepo.batchState.collectAsStateWithLifecycle()

                SnippetsPopupContent(
                    config = config,
                    batchState = batch,
                    onCopySnippet = { snippet ->
                        val text = if (snippet.includeTriggerWord) {
                            PromptTextComposer.applyTriggerWord(snippet.body, config.effectiveTriggerWord)
                        } else {
                            snippet.body.trim()
                        }
                        if (ClipboardHelper.writePlainText(this, "PromptShift Snippet", text)) {
                            configRepo.recordRecentPrompt(text)
                            batchRepo.postFeedback("Copied: ${snippet.title.take(16)}")
                        }
                        finish()
                    },
                    onCopyRecent = { recentText ->
                        if (ClipboardHelper.writePlainText(this, "PromptShift Recent", recentText)) {
                            configRepo.recordRecentPrompt(recentText)
                            batchRepo.postFeedback("Copied recent ✅")
                        }
                        finish()
                    },
                    onSaveCurrentSlot = {
                        val currentPrompt = batch.currentPrompt
                        if (!currentPrompt.isNullOrBlank()) {
                            val autoTitle = currentPrompt.trim()
                                .replace('\n', ' ')
                                .take(24)
                                .ifBlank { "Slot #${batch.displayIndex}" }
                            configRepo.saveSnippet(
                                id = null,
                                title = autoTitle,
                                body = currentPrompt,
                                includeTriggerWord = true,
                            )
                            batchRepo.postFeedback("Saved snippet 📌")
                        }
                    },
                    onClearRecent = {
                        configRepo.clearRecentPrompts()
                        batchRepo.postFeedback("Recent cleared")
                    },
                    onDismiss = { finish() },
                )
            }
        }
    }

    companion object {
        fun launch(context: Context) {
            val intent = Intent(context, SnippetsPopupActivity::class.java).apply {
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
private fun SnippetsPopupContent(
    config: AiConfigState,
    batchState: PromptBatchState,
    onCopySnippet: (PromptSnippet) -> Unit,
    onCopyRecent: (String) -> Unit,
    onSaveCurrentSlot: () -> Unit,
    onClearRecent: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Saved Snippets, 1 = Recent 10

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
                        text = "SNIPPETS & RECENT",
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

                // 1-tap save current prompt slot
                if (!batchState.isEmpty) {
                    OutlinedButton(
                        onClick = onSaveCurrentSlot,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PromptShiftColors.GtCrimson.copy(alpha = 0.8f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = PromptShiftColors.CarbonElevated,
                            contentColor = PromptShiftColors.TextPrimary,
                        ),
                    ) {
                        Text(
                            text = "+ Save Current Slot #${batchState.displayIndex} as Snippet",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Tabs: Saved Snippets / Recent 10
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(PromptShiftColors.ObsidianBg)
                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PopupTabButton(
                        modifier = Modifier.weight(1f),
                        title = "Saved (${config.snippets.size})",
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                    )
                    PopupTabButton(
                        modifier = Modifier.weight(1f),
                        title = "Recent (${config.recentPrompts.size})",
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (selectedTab == 0) {
                        if (config.snippets.isEmpty()) {
                            Text(
                                text = "No saved snippets yet. Tap \"+ Save Current Slot\" above or create snippets in the Main Hub.",
                                color = PromptShiftColors.TextMuted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        } else {
                            config.snippets.forEach { snippet ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PromptShiftColors.ObsidianBg)
                                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                                        .clickable { onCopySnippet(snippet) }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = snippet.title,
                                            color = PromptShiftColors.TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (snippet.includeTriggerWord) {
                                            Text(
                                                text = "+Trigger",
                                                color = PromptShiftColors.CopiedEmerald,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Text(
                                        text = snippet.body,
                                        color = PromptShiftColors.TextMuted,
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    } else {
                        if (config.recentPrompts.isEmpty()) {
                            Text(
                                text = "Your last 10 copied or inserted prompts will appear here automatically.",
                                color = PromptShiftColors.TextMuted,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                            )
                        } else {
                            config.recentPrompts.forEachIndexed { idx, recent ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(PromptShiftColors.ObsidianBg)
                                        .border(1.dp, PromptShiftColors.MetalBorder, RoundedCornerShape(12.dp))
                                        .clickable { onCopyRecent(recent) }
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = "RECENT #${idx + 1} • ${recent.length} chars",
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
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PromptShiftColors.MetalBorder),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PromptShiftColors.TextMuted),
                            ) {
                                Text("Clear Recent History", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupTabButton(
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
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            color = if (selected) PromptShiftColors.TextPrimary else PromptShiftColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
