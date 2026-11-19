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
import android.widget.LinearLayout
import android.widget.TextView
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.ImePrefsState
import com.promptshift.app.domain.OverlayFeedback
import com.promptshift.app.domain.PromptBatchState
import kotlin.math.roundToInt

/**
 * Compact GT Carbon & Crimson Mini-Keyboard view controller for PromptShift Auto-Insert.
 */
class PromptShiftImeViewController(
    private val context: Context,
    private val onSwitchKeyboardClicked: () -> Unit,
    private val onAiClicked: () -> Unit,
    private val onCancelStreamClicked: () -> Unit,
    private val onLoadClicked: () -> Unit,
    private val onToggleReplaceMode: () -> Unit,
    private val onToggleAutoNext: () -> Unit,
    private val onHideKeyboardClicked: () -> Unit,
    private val onPreviousClicked: () -> Unit,
    private val onInsertClicked: () -> Unit,
    private val onClearBoxClicked: () -> Unit,
    private val onNextClicked: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = context.resources.displayMetrics.density

    private var aiButton: TextView? = null
    private var modeButton: TextView? = null
    private var autoNextButton: TextView? = null
    private var statusCountText: TextView? = null
    private var prevButton: TextView? = null
    private var insertButton: TextView? = null
    private var nextButton: TextView? = null

    private var latestBatch = PromptBatchState()
    private var latestPrefs = ImePrefsState()
    private var latestGeneration: GenerationState = GenerationState.Idle
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

        // Row 1: [🌐] [AI] [Load] [Replace] [Auto>]  1840/2000  [⌄]
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
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }

        val aiBtn = makeSmallChip("AI", "Quick Direction AI Generate") { view ->
            performHaptic(view)
            if (latestGeneration.isStreaming) {
                onCancelStreamClicked()
            } else {
                onAiClicked()
            }
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }
        aiButton = aiBtn

        val loadBtn = makeSmallChip("Load", "Load batch from clipboard") { view ->
            performHaptic(view)
            onLoadClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }

        val modeBtn = makeSmallChip("Replace", "Toggle Replace or Append mode") { view ->
            performHaptic(view)
            onToggleReplaceMode()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }
        modeButton = modeBtn

        val autoBtn = makeSmallChip("Auto>", "Toggle Auto-Next after insert") { view ->
            performHaptic(view)
            onToggleAutoNext()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(5)
        }
        autoNextButton = autoBtn

        val statusTv = TextView(context).apply {
            text = "0/2000"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setTextColor(TEXT_MUTED)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(5)
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
        topRow.addView(modeBtn)
        topRow.addView(autoBtn)
        topRow.addView(statusTv)
        topRow.addView(hideBtn)

        // Row 2: [ < ]  [ Insert #3/20 ]  [⌫]  [ > ]
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

    fun renderGenerationState(generationState: GenerationState) {
        latestGeneration = generationState
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

    private fun render() {
        val state = latestBatch
        val prefs = latestPrefs
        val genState = latestGeneration
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

        modeButton?.let { btn ->
            btn.text = if (prefs.replaceMode) "Replace" else "Append"
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

        statusCountText?.let { tv ->
            val fb = activeFeedback
            when {
                fb != null -> {
                    tv.text = fb.message
                    tv.setTextColor(if (fb.isError) QUOTA_WARN_RED else COPIED_EMERALD)
                    tv.setTypeface(Typeface.DEFAULT_BOLD)
                }
                state.isCurrentExceedingQuota -> {
                    tv.text = state.charCountLabel()
                    tv.setTextColor(QUOTA_WARN_RED)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }
                genState is GenerationState.Streaming -> {
                    tv.text = "Live ${genState.unlockedCount}/${genState.targetCount}"
                    tv.setTextColor(COPIED_EMERALD)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                }
                else -> {
                    tv.text = state.charCountLabel()
                    tv.setTextColor(TEXT_MUTED)
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
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = description
            setPadding(dp(7), 0, dp(7), 0)
            background = makeButtonBg(CARBON_CHIP, CARBON_CHIP_PRESSED, METAL_BORDER, 8f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(30),
            )
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
