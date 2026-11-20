package com.promptshift.app.ime

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.promptshift.app.domain.AiConfigState
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.ImePrefsState
import com.promptshift.app.domain.OverlayFeedback
import com.promptshift.app.domain.PromptBatchState
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.timer.TimerState
import kotlin.math.roundToInt

/**
 * Compact GT Carbon & Crimson Mini-Keyboard view controller for PromptShift Auto-Insert.
 * Phase 4 adds:
 * - Trigger-Word-synced 2,000-char counter
 * - Live [⏱] countdown timer pill
 * - Scrollable Saved Snippets & Recent 10 chip strip with [Snip: + / Rep] mode toggle
 */
class PromptShiftImeViewController(
    private val context: Context,
    private val onSwitchKeyboardClicked: () -> Unit,
    private val onAiClicked: () -> Unit,
    private val onCancelStreamClicked: () -> Unit,
    private val onLoadClicked: () -> Unit,
    private val onTimerClicked: () -> Unit,
    private val onToggleReplaceMode: () -> Unit,
    private val onToggleAutoNext: () -> Unit,
    private val onToggleSnippetReplaceMode: () -> Unit,
    private val onSnippetClicked: (PromptSnippet) -> Unit,
    private val onRecentClicked: (String) -> Unit,
    private val onSaveCurrentSlotClicked: () -> Unit,
    private val onHideKeyboardClicked: () -> Unit,
    private val onPreviousClicked: () -> Unit,
    private val onInsertClicked: () -> Unit,
    private val onClearBoxClicked: () -> Unit,
    private val onNextClicked: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var aiButton: TextView? = null
    private var timerButton: TextView? = null
    private var modeButton: TextView? = null
    private var autoNextButton: TextView? = null
    private var statusCountText: TextView? = null
    private var snippetStripRow: LinearLayout? = null
    private var snippetModeButton: TextView? = null
    private var saveSlotChipButton: TextView? = null
    private var snippetChipsContainer: LinearLayout? = null
    private var prevButton: TextView? = null
    private var insertButton: TextView? = null
    private var nextButton: TextView? = null

    private var latestBatch = PromptBatchState()
    private var latestPrefs = ImePrefsState()
    private var latestConfig = AiConfigState()
    private var latestGeneration: GenerationState = GenerationState.Idle
    private var latestTimer = TimerState()
    private var activeFeedback: OverlayFeedback? = null

    private val feedbackTimeout = Runnable {
        activeFeedback = null
        render()
    }

    fun createView(): View {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(OBSIDIAN_BG)
            setPadding(dp(10), dp(8), dp(10), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(CARBON_SURFACE)
                setStroke(dp(1), METAL_BORDER)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        // Row 1: [🌐] [AI] [Load] [⏱] [Rep] [Auto>]  1840/2000  [⌄]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val switchKbBtn = makeSmallChip("🌐", "Switch back to previous keyboard") { view ->
            performHaptic(view)
            onSwitchKeyboardClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }

        val aiBtn = makeSmallChip("AI", "Quick Direction AI Generate") { view ->
            performHaptic(view)
            if (latestGeneration.isStreaming) {
                onCancelStreamClicked()
            } else {
                onAiClicked()
            }
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }
        aiButton = aiBtn

        val loadBtn = makeSmallChip("Load", "Load batch from clipboard") { view ->
            performHaptic(view)
            onLoadClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }

        val timerBtn = makeSmallChip("⏱", "Countdown Timer") { view ->
            performHaptic(view)
            onTimerClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }
        timerButton = timerBtn

        val modeBtn = makeSmallChip("Rep", "Toggle Replace or Append mode") { view ->
            performHaptic(view)
            onToggleReplaceMode()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }
        modeButton = modeBtn

        val autoBtn = makeSmallChip("Auto>", "Toggle Auto-Next after insert") { view ->
            performHaptic(view)
            onToggleAutoNext()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(4)
        }
        autoNextButton = autoBtn

        val statusTv = TextView(context).apply {
            text = "0/2000"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextColor(TEXT_MUTED)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(4)
            }
        }
        statusCountText = statusTv

        val hideBtn = makeSmallChip("⌄", "Hide keyboard") { view ->
            performHaptic(view)
            onHideKeyboardClicked()
        }

        topRow.addView(switchKbBtn)
        topRow.addView(aiBtn)
        topRow.addView(loadBtn)
        topRow.addView(timerBtn)
        topRow.addView(modeBtn)
        topRow.addView(autoBtn)
        topRow.addView(statusTv)
        topRow.addView(hideBtn)

        // Row 2: [Snip: +] [+Snip] | Scrollable Saved Snippets & Recent 10 chips
        val snipRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(6)
            }
        }
        snippetStripRow = snipRow

        val snipModeBtn = makeSmallChip("Snip:+", "Toggle Snippet Append or Replace mode") { view ->
            performHaptic(view)
            onToggleSnippetReplaceMode()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }
        snippetModeButton = snipModeBtn

        val saveSlotBtn = makeSmallChip("+Snip", "Save current slot as snippet") { view ->
            performHaptic(view)
            onSaveCurrentSlotClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }
        saveSlotChipButton = saveSlotBtn

        val chipsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        snippetChipsContainer = chipsContainer

        val chipScroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(chipsContainer)
        }

        snipRow.addView(snipModeBtn)
        snipRow.addView(saveSlotBtn)
        snipRow.addView(chipScroller)

        // Row 3: [ < ]  [ Insert #3/20 ]  [⌫]  [ > ]
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = dp(8)
            }
        }

        val prevBtn = makeNavButton("<", "Previous prompt") { view ->
            performHaptic(view)
            onPreviousClicked()
        }
        prevButton = prevBtn

        val insertBtn = TextView(context).apply {
            text = "Tap Load or AI"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(6)
            }
            setOnClickListener { view ->
                if (latestBatch.isEmpty && latestGeneration.isStreaming) return@setOnClickListener
                performHaptic(view)
                if (latestBatch.isEmpty) {
                    onLoadClicked()
                } else {
                    onInsertClicked()
                }
            }
        }
        insertButton = insertBtn

        val clearBoxBtn = makeNavButton("⌫", "Clear input box") { view ->
            performHaptic(view)
            onClearBoxClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
        }

        val nextBtn = makeNavButton(">", "Next prompt") { view ->
            performHaptic(view)
            onNextClicked()
        }
        nextButton = nextBtn

        bottomRow.addView(prevBtn)
        bottomRow.addView(insertBtn)
        bottomRow.addView(clearBoxBtn)
        bottomRow.addView(nextBtn)

        card.addView(topRow)
        card.addView(snipRow)
        card.addView(bottomRow)
        outer.addView(card)

        render()
        return outer
    }

    fun renderBatchState(state: PromptBatchState) {
        latestBatch = state
        render()
    }

    fun renderImePrefs(prefs: ImePrefsState) {
        latestPrefs = prefs
        render()
    }

    fun renderAiConfig(config: AiConfigState) {
        latestConfig = config
        rebuildSnippetChips()
        render()
    }

    fun renderGenerationState(generationState: GenerationState) {
        latestGeneration = generationState
        render()
    }

    fun renderTimerState(timerState: TimerState) {
        latestTimer = timerState
        render()
    }

    fun showFeedback(feedback: OverlayFeedback) {
        activeFeedback = feedback
        mainHandler.removeCallbacks(feedbackTimeout)
        mainHandler.postDelayed(feedbackTimeout, FEEDBACK_DURATION_MS)
        render()
    }

    fun destroy() {
        mainHandler.removeCallbacks(feedbackTimeout)
    }

    private fun rebuildSnippetChips() {
        val container = snippetChipsContainer ?: return
        container.removeAllViews()

        val snippets = latestConfig.snippets
        val recents = latestConfig.recentPrompts

        if (snippets.isEmpty() && recents.isEmpty()) {
            val hint = TextView(context).apply {
                text = "Save snippets in Hub or tap +Snip"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(TEXT_MUTED)
                setPadding(dp(4), 0, dp(4), 0)
            }
            container.addView(hint)
            return
        }

        snippets.forEach { snippet ->
            val label = "📌 ${snippet.title.take(16)}"
            val chip = makeSnippetChip(
                label = label,
                isSavedSnippet = true,
            ) { view ->
                performHaptic(view)
                onSnippetClicked(snippet)
            }
            container.addView(chip)
        }

        recents.forEach { recentText ->
            val compact = recentText.replace('\n', ' ').trim()
            val label = "↺ ${compact.take(16)}${if (compact.length > 16) "…" else ""}"
            val chip = makeSnippetChip(
                label = label,
                isSavedSnippet = false,
            ) { view ->
                performHaptic(view)
                onRecentClicked(recentText)
            }
            container.addView(chip)
        }
    }

    private fun render() {
        val state = latestBatch
        val prefs = latestPrefs
        val genState = latestGeneration
        val timerState = latestTimer
        val activeTrigger = latestConfig.effectiveTriggerWord
        val isStreaming = genState is GenerationState.Streaming

        aiButton?.let { btn ->
            if (isStreaming) {
                btn.text = "Stop"
                btn.setTextColor(TEXT_PRIMARY)
                btn.background = makeButtonBg(GT_CRIMSON, GT_CRIMSON_GLOW, GT_CRIMSON_GLOW, 8f)
            } else {
                btn.text = "AI"
                btn.setTextColor(TEXT_PRIMARY)
                btn.background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, GT_CRIMSON, 8f)
            }
        }

        timerButton?.let { btn ->
            btn.text = timerState.chipLabel()
            when {
                timerState.isRunning -> {
                    btn.setTextColor(COPIED_EMERALD)
                    btn.typeface = Typeface.MONOSPACE
                    btn.background = makeButtonBg(COPIED_EMERALD_SURFACE, COPIED_EMERALD_SURFACE_PRESSED, COPIED_EMERALD, 8f)
                }
                timerState.isFinished -> {
                    btn.setTextColor(TEXT_PRIMARY)
                    btn.typeface = Typeface.DEFAULT_BOLD
                    btn.background = makeButtonBg(GT_CRIMSON, GT_CRIMSON_GLOW, GT_CRIMSON_GLOW, 8f)
                }
                else -> {
                    btn.setTextColor(TEXT_PRIMARY)
                    btn.typeface = Typeface.DEFAULT_BOLD
                    btn.background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, METAL_BORDER, 8f)
                }
            }
        }

        modeButton?.let { btn ->
            btn.text = if (prefs.replaceMode) "Rep" else "App"
            btn.setTextColor(if (prefs.replaceMode) TEXT_PRIMARY else TEXT_MUTED)
            btn.background = makeButtonBg(
                fill = if (prefs.replaceMode) CARBON_CHIP else OBSIDIAN_BG,
                pressed = CARBON_CHIP_PRESSED,
                stroke = if (prefs.replaceMode) METAL_BORDER_LIGHT else METAL_BORDER,
                radiusDp = 8f,
            )
        }

        autoNextButton?.let { btn ->
            btn.text = "Auto>"
            if (prefs.autoNext) {
                btn.setTextColor(COPIED_EMERALD)
                btn.background = makeButtonBg(
                    fill = COPIED_EMERALD_SURFACE,
                    pressed = COPIED_EMERALD_SURFACE_PRESSED,
                    stroke = COPIED_EMERALD,
                    radiusDp = 8f,
                )
            } else {
                btn.setTextColor(TEXT_MUTED)
                btn.background = makeButtonBg(OBSIDIAN_BG, CARBON_CHIP_PRESSED, METAL_BORDER, 8f)
            }
        }

        snippetModeButton?.let { btn ->
            btn.text = if (prefs.snippetReplaceMode) "Snip:Rep" else "Snip:+"
            if (prefs.snippetReplaceMode) {
                btn.setTextColor(TEXT_PRIMARY)
                btn.background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, GT_CRIMSON, 8f)
            } else {
                btn.setTextColor(TEXT_MUTED)
                btn.background = makeButtonBg(OBSIDIAN_BG, CARBON_CHIP_PRESSED, METAL_BORDER, 8f)
            }
        }

        saveSlotChipButton?.visibility = if (state.isEmpty) View.GONE else View.VISIBLE

        val hasAnyChipsOrBatch = !state.isEmpty ||
            latestConfig.snippets.isNotEmpty() ||
            latestConfig.recentPrompts.isNotEmpty()
        snippetStripRow?.visibility = if (hasAnyChipsOrBatch) View.VISIBLE else View.GONE

        statusCountText?.let { tv ->
            val fb = activeFeedback
            val exceedsQuota = state.isCurrentExceedingQuota(activeTrigger)
            when {
                fb != null -> {
                    tv.text = fb.message
                    tv.setTextColor(if (fb.isError) QUOTA_WARN_RED else COPIED_EMERALD)
                    tv.setTypeface(Typeface.DEFAULT_BOLD)
                }
                exceedsQuota -> {
                    tv.text = state.charCountLabel(activeTrigger)
                    tv.setTextColor(QUOTA_WARN_RED)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }
                genState is GenerationState.Streaming -> {
                    tv.text = "Live ${genState.unlockedCount}/${genState.targetCount}"
                    tv.setTextColor(COPIED_EMERALD)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }
                else -> {
                    tv.text = state.charCountLabel(activeTrigger)
                    tv.setTextColor(if (activeTrigger != null && !state.isEmpty) COPIED_EMERALD else TEXT_MUTED)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
                }
            }
        }

        prevButton?.let { btn ->
            btn.isEnabled = state.canGoPrevious
            btn.alpha = if (state.canGoPrevious) 1f else 0.35f
        }
        nextButton?.let { btn ->
            btn.isEnabled = state.canGoNext
            btn.alpha = if (state.canGoNext) 1f else 0.35f
        }

        insertButton?.let { btn ->
            when {
                state.isEmpty && isStreaming -> {
                    btn.text = "Streaming..."
                    btn.setTextColor(COPIED_EMERALD)
                    btn.background = makeButtonBg(COPIED_EMERALD_SURFACE, COPIED_EMERALD_SURFACE, COPIED_EMERALD, 12f)
                }
                state.isEmpty -> {
                    btn.text = state.insertButtonLabel()
                    btn.setTextColor(TEXT_PRIMARY)
                    btn.background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, METAL_BORDER, 12f)
                }
                state.isCurrentCopied -> {
                    btn.text = state.insertButtonLabel()
                    btn.setTextColor(COPIED_EMERALD)
                    btn.background = makeButtonBg(
                        fill = COPIED_EMERALD_SURFACE,
                        pressed = COPIED_EMERALD_SURFACE_PRESSED,
                        stroke = COPIED_EMERALD,
                        strokeWidthDp = 1.5f,
                        radiusDp = 12f,
                    )
                }
                else -> {
                    btn.text = state.insertButtonLabel()
                    btn.setTextColor(TEXT_PRIMARY)
                    btn.background = makeButtonBg(GT_CRIMSON, GT_CRIMSON_GLOW, GT_CRIMSON_GLOW, 12f)
                }
            }
        }
    }

    private fun makeSmallChip(label: String, description: String, onClick: (View) -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = description
            setPadding(dp(6), 0, dp(6), 0)
            background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, METAL_BORDER, 8f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28),
            )
            setOnClickListener(onClick)
        }
    }

    private fun makeSnippetChip(
        label: String,
        isSavedSnippet: Boolean,
        onClick: (View) -> Unit,
    ): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(if (isSavedSnippet) TEXT_PRIMARY else TEXT_MUTED)
            typeface = if (isSavedSnippet) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(dp(8), 0, dp(8), 0)
            background = makeButtonBg(
                fill = if (isSavedSnippet) CARBON_CHIP else OBSIDIAN_BG,
                pressed = CARBON_CHIP_PRESSED,
                stroke = if (isSavedSnippet) METAL_BORDER_LIGHT else METAL_BORDER,
                radiusDp = 8f,
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28),
            ).apply {
                marginEnd = dp(5)
            }
            setOnClickListener(onClick)
        }
    }

    private fun makeNavButton(symbol: String, description: String, onClick: (View) -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = description
            background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, METAL_BORDER, 12f)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(46))
            setOnClickListener(onClick)
        }
    }

    private fun makeButtonBg(
        fill: Int,
        pressed: Int,
        stroke: Int,
        radiusDp: Float,
        strokeWidthDp: Float = 1f,
    ): StateListDrawable {
        val strokePx = (strokeWidthDp * density).roundToInt().coerceAtLeast(1)
        val radiusPx = radiusDp * density
        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(pressed)
            setStroke(strokePx, stroke)
        }
        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fill)
            setStroke(strokePx, stroke)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    private fun performHaptic(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    companion object {
        private const val FEEDBACK_DURATION_MS = 2200L

        private val OBSIDIAN_BG = Color.parseColor("#0A0A0D")
        private val CARBON_SURFACE = Color.parseColor("#141419")
        private val CARBON_CHIP = Color.parseColor("#1D1D24")
        private val CARBON_CHIP_PRESSED = Color.parseColor("#2A2A34")
        private val METAL_BORDER = Color.parseColor("#282830")
        private val METAL_BORDER_LIGHT = Color.parseColor("#424250")
        private val GT_CRIMSON = Color.parseColor("#D61F26")
        private val GT_CRIMSON_GLOW = Color.parseColor("#FF2E38")
        private val TEXT_PRIMARY = Color.parseColor("#F5F5F7")
        private val TEXT_MUTED = Color.parseColor("#9A9AA6")
        private val COPIED_EMERALD = Color.parseColor("#22C55E")
        private val COPIED_EMERALD_SURFACE = Color.parseColor("#143020")
        private val COPIED_EMERALD_SURFACE_PRESSED = Color.parseColor("#1C452E")
        private val QUOTA_WARN_RED = Color.parseColor("#FF4D4F")
    }
}
