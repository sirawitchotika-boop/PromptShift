package com.promptshift.app.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.promptshift.app.domain.OverlayFeedback
import com.promptshift.app.domain.PromptBatchState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Lightweight native View controller for the GT Carbon & Crimson floating overlay.
 * Renders both the Expanded Pill and Collapsed Dot directly in WindowManager for
 * 120Hz smooth dragging and minimal RAM usage on Realme UI / Android 14.
 */
class FloatingOverlayViewController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onLoadClicked: () -> Unit,
    private val onClearConfirmed: () -> Unit,
    private val onCloseClicked: () -> Unit,
    private val onPreviousClicked: () -> Unit,
    private val onNextClicked: () -> Unit,
    private val onCopyClicked: () -> Unit,
    private val onCollapsedChanged: (Boolean) -> Unit,
    private val onPositionCommitted: (Int, Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = context.resources.displayMetrics.density

    private var rootContainer: FrameLayout? = null
    private var expandedView: View? = null
    private var collapsedView: View? = null

    // Expanded Pill views
    private var clearButton: TextView? = null
    private var statusCountText: TextView? = null
    private var prevButton: TextView? = null
    private var nextButton: TextView? = null
    private var copyButton: TextView? = null

    // Collapsed Dot views
    private var collapsedSlotText: TextView? = null
    private var collapsedCopiedDot: View? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached = false
    private var isCollapsed = false
    private var isAwaitingClearConfirm = false
    private var latestState = PromptBatchState()
    private var activeFeedback: OverlayFeedback? = null
    private var snapAnimator: ValueAnimator? = null

    private val clearConfirmTimeout = Runnable {
        isAwaitingClearConfirm = false
        renderBatchState(latestState)
    }

    private val feedbackTimeout = Runnable {
        activeFeedback = null
        renderBatchState(latestState)
    }

    fun attach(initialX: Int, initialY: Int, hasSavedPosition: Boolean, collapsed: Boolean) {
        if (isAttached) return
        isCollapsed = collapsed

        val container = FrameLayout(context)
        val expanded = buildExpandedPill()
        val collapsedDot = buildCollapsedDot()
        container.addView(expanded)
        container.addView(collapsedDot)

        expanded.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapsedDot.visibility = if (collapsed) View.VISIBLE else View.GONE

        rootContainer = container
        expandedView = expanded
        collapsedView = collapsedDot

        val metrics = context.resources.displayMetrics
        val defaultX = if (hasSavedPosition) initialX else dp(12)
        val defaultY = if (hasSavedPosition) initialY else (metrics.heightPixels * 0.16f).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultX
            y = defaultY
        }

        layoutParams = params
        windowManager.addView(container, params)
        isAttached = true

        renderBatchState(latestState)
        container.post { clampToScreenBounds(persist = false) }
    }

    fun detach() {
        snapAnimator?.cancel()
        snapAnimator = null
        mainHandler.removeCallbacks(clearConfirmTimeout)
        mainHandler.removeCallbacks(feedbackTimeout)
        if (isAttached) {
            rootContainer?.let { view ->
                try {
                    windowManager.removeViewImmediate(view)
                } catch (_: Exception) {
                }
            }
            isAttached = false
        }
        rootContainer = null
        expandedView = null
        collapsedView = null
        layoutParams = null
    }

    fun renderBatchState(state: PromptBatchState) {
        latestState = state
        if (state.isEmpty && isAwaitingClearConfirm) {
            isAwaitingClearConfirm = false
            mainHandler.removeCallbacks(clearConfirmTimeout)
        }

        // Update Clear button (2-tap confirmation state)
        clearButton?.let { btn ->
            val enabled = !state.isEmpty
            btn.isEnabled = enabled
            btn.alpha = if (enabled) 1f else 0.4f
            if (isAwaitingClearConfirm && enabled) {
                btn.text = "Confirm?"
                btn.setTextColor(TEXT_PRIMARY)
                btn.background = makeButtonBackground(
                    fillColor = GT_CRIMSON,
                    pressedColor = GT_CRIMSON_GLOW,
                    strokeColor = GT_CRIMSON_GLOW,
                    radiusDp = 8f,
                )
            } else {
                btn.text = "Clear"
                btn.setTextColor(TEXT_PRIMARY)
                btn.background = makeButtonBackground(
                    fillColor = CARBON_CHIP,
                    pressedColor = CARBON_CHIP_PRESSED,
                    strokeColor = METAL_BORDER,
                    radiusDp = 8f,
                )
            }
        }

        // Update char count / temporary feedback slot
        statusCountText?.let { tv ->
            val fb = activeFeedback
            if (fb != null) {
                tv.text = fb.message
                tv.setTextColor(if (fb.isError) QUOTA_WARN_RED else COPIED_EMERALD)
                tv.setTypeface(Typeface.DEFAULT_BOLD)
            } else {
                tv.text = state.charCountLabel()
                if (state.isCurrentExceedingQuota) {
                    tv.setTextColor(QUOTA_WARN_RED)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                } else {
                    tv.setTextColor(TEXT_MUTED)
                    tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
                }
            }
        }

        // Update Prev / Next buttons (no wrap-around)
        prevButton?.let { btn ->
            btn.isEnabled = state.canGoPrevious
            btn.alpha = if (state.canGoPrevious) 1f else 0.35f
        }
        nextButton?.let { btn ->
            btn.isEnabled = state.canGoNext
            btn.alpha = if (state.canGoNext) 1f else 0.35f
        }

        // Update Center Copy CTA
        copyButton?.let { btn ->
            btn.text = state.copyButtonLabel()
            when {
                state.isEmpty -> {
                    btn.setTextColor(TEXT_PRIMARY)
                    btn.background = makeButtonBackground(
                        fillColor = CARBON_CHIP,
                        pressedColor = CARBON_CHIP_PRESSED,
                        strokeColor = METAL_BORDER,
                        radiusDp = 10f,
                    )
                }
                state.isCurrentCopied -> {
                    btn.setTextColor(COPIED_EMERALD)
                    btn.background = makeButtonBackground(
                        fillColor = COPIED_EMERALD_SURFACE,
                        pressedColor = COPIED_EMERALD_SURFACE_PRESSED,
                        strokeColor = COPIED_EMERALD,
                        strokeWidthDp = 1.5f,
                        radiusDp = 10f,
                    )
                }
                else -> {
                    btn.setTextColor(TEXT_PRIMARY)
                    btn.background = makeButtonBackground(
                        fillColor = GT_CRIMSON,
                        pressedColor = GT_CRIMSON_GLOW,
                        strokeColor = GT_CRIMSON_GLOW,
                        radiusDp = 10f,
                    )
                }
            }
        }

        // Update Collapsed Dot
        collapsedSlotText?.text = state.collapsedBadgeLabel()
        collapsedCopiedDot?.visibility = if (state.isCurrentCopied) View.VISIBLE else View.GONE
    }

    fun showFeedback(feedback: OverlayFeedback) {
        activeFeedback = feedback
        mainHandler.removeCallbacks(feedbackTimeout)
        mainHandler.postDelayed(feedbackTimeout, FEEDBACK_DURATION_MS)
        renderBatchState(latestState)
    }

    fun setCollapsed(collapsed: Boolean) {
        if (isCollapsed == collapsed) return
        snapAnimator?.cancel()
        isCollapsed = collapsed
        if (collapsed) {
            isAwaitingClearConfirm = false
            mainHandler.removeCallbacks(clearConfirmTimeout)
        }
        expandedView?.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapsedView?.visibility = if (collapsed) View.VISIBLE else View.GONE
        rootContainer?.post {
            if (collapsed) {
                snapCollapsedToNearestEdge()
            } else {
                clampToScreenBounds(persist = true)
            }
        }
    }

    fun onDisplayBoundsChanged() {
        rootContainer?.post {
            if (isCollapsed) {
                snapCollapsedToNearestEdge()
            } else {
                clampToScreenBounds(persist = true)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildExpandedPill(): View {
        val pill = DragContainerLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            elevation = dp(8).toFloat()
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(CARBON_SURFACE_94)
                setStroke(dp(1), METAL_BORDER)
            }
            onDragDelta = { dx, dy -> moveBy(dx, dy) }
            onDragEnd = { clampToScreenBounds(persist = true) }
        }

        // Top row: [●] [Load] [Clear]  1840/2000  [✕]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        val collapseBtn = TextView(context).apply {
            text = "●"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(GT_CRIMSON)
            gravity = Gravity.CENTER
            contentDescription = "Collapse bubble"
            background = makeButtonBackground(
                fillColor = CARBON_CHIP,
                pressedColor = CARBON_CHIP_PRESSED,
                strokeColor = METAL_BORDER,
                radiusDp = 14f,
            )
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                performSubtleHaptic(it)
                onCollapsedChanged(true)
            }
        }

        val loadBtn = makeTopChipButton("Load") { view ->
            performSubtleHaptic(view)
            onLoadClicked()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(6)
        }

        val clearBtn = makeTopChipButton("Clear") { view ->
            if (latestState.isEmpty) return@makeTopChipButton
            performSubtleHaptic(view)
            if (isAwaitingClearConfirm) {
                isAwaitingClearConfirm = false
                mainHandler.removeCallbacks(clearConfirmTimeout)
                onClearConfirmed()
            } else {
                isAwaitingClearConfirm = true
                mainHandler.removeCallbacks(clearConfirmTimeout)
                mainHandler.postDelayed(clearConfirmTimeout, CLEAR_CONFIRM_TIMEOUT_MS)
                renderBatchState(latestState)
            }
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(8)
        }
        clearButton = clearBtn

        val countTv = TextView(context).apply {
            text = "0/2000"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(TEXT_MUTED)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = dp(76)
            maxWidth = dp(122)
            setPadding(dp(2), 0, dp(2), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginEnd = dp(8)
            }
        }
        statusCountText = countTv

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(TEXT_MUTED)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            contentDescription = "Close floating bubble"
            background = makeButtonBackground(
                fillColor = CARBON_CHIP,
                pressedColor = CARBON_CHIP_PRESSED,
                strokeColor = METAL_BORDER,
                radiusDp = 14f,
            )
            layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
            setOnClickListener {
                performSubtleHaptic(it)
                onCloseClicked()
            }
        }

        topRow.addView(collapseBtn)
        topRow.addView(loadBtn)
        topRow.addView(clearBtn)
        topRow.addView(countTv)
        topRow.addView(closeBtn)

        // Bottom row: [ < ]  [ Copy #3/20 ]  [ > ]
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
            performSubtleHaptic(view)
            onPreviousClicked()
        }
        prevButton = prevBtn

        val copyBtn = TextView(context).apply {
            text = "Tap Load"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(14), 0, dp(14), 0)
            minWidth = dp(156)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginStart = dp(8)
                marginEnd = dp(8)
            }
            setOnClickListener { view ->
                performSubtleHaptic(view)
                if (latestState.isEmpty) {
                    onLoadClicked()
                } else {
                    onCopyClicked()
                }
            }
        }
        copyButton = copyBtn

        val nextBtn = makeNavButton(">", "Next prompt") { view ->
            performSubtleHaptic(view)
            onNextClicked()
        }
        nextButton = nextBtn

        bottomRow.addView(prevBtn)
        bottomRow.addView(copyBtn)
        bottomRow.addView(nextBtn)

        pill.addView(topRow)
        pill.addView(bottomRow)
        return pill
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildCollapsedDot(): View {
        val badgeSize = dp(48)
        val badge = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize)
            elevation = dp(8).toFloat()
            contentDescription = "Expand PromptShift"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CARBON_SURFACE_94)
                setStroke(dp(2), GT_CRIMSON)
            }
        }

        val slotText = TextView(context).apply {
            text = "•"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        collapsedSlotText = slotText

        val copiedDot = View(context).apply {
            visibility = View.GONE
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(COPIED_EMERALD)
                setStroke(dp(1), OBSIDIAN_BG)
            }
            layoutParams = FrameLayout.LayoutParams(dp(10), dp(10)).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dp(5)
                marginEnd = dp(5)
            }
        }
        collapsedCopiedDot = copiedDot

        badge.addView(slotText)
        badge.addView(copiedDot)

        // Smooth drag + edge snap on release + single tap to expand
        var downRawX = 0f
        var downRawY = 0f
        var lastRawX = 0f
        var lastRawY = 0f
        var isDragging = false

        badge.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY
                    if (!isDragging && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val stepDx = (event.rawX - lastRawX).roundToInt()
                        val stepDy = (event.rawY - lastRawY).roundToInt()
                        moveBy(stepDx, stepDy)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapCollapsedToNearestEdge()
                    } else {
                        performSubtleHaptic(view)
                        onCollapsedChanged(false)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        snapCollapsedToNearestEdge()
                    }
                    true
                }
                else -> false
            }
        }

        return badge
    }

    private fun makeTopChipButton(label: String, onClick: (View) -> Unit): TextView {
        return TextView(context).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(9), 0, dp(9), 0)
            background = makeButtonBackground(
                fillColor = CARBON_CHIP,
                pressedColor = CARBON_CHIP_PRESSED,
                strokeColor = METAL_BORDER,
                radiusDp = 8f,
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(28),
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
            background = makeButtonBackground(
                fillColor = CARBON_CHIP,
                pressedColor = CARBON_CHIP_PRESSED,
                strokeColor = METAL_BORDER,
                radiusDp = 10f,
            )
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(40))
            setOnClickListener(onClick)
        }
    }

    private fun makeButtonBackground(
        fillColor: Int,
        pressedColor: Int,
        strokeColor: Int,
        strokeWidthDp: Float = 1f,
        radiusDp: Float,
    ): StateListDrawable {
        val strokePx = (strokeWidthDp * density).roundToInt().coerceAtLeast(1)
        val radiusPx = radiusDp * density

        val pressedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(pressedColor)
            setStroke(strokePx, strokeColor)
        }
        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            setStroke(strokePx, strokeColor)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressedDrawable)
            addState(intArrayOf(), normalDrawable)
        }
    }

    private fun moveBy(dx: Int, dy: Int) {
        val params = layoutParams ?: return
        val root = rootContainer ?: return
        if (!isAttached) return

        val (clampedX, clampedY) = computeClampedPosition(params.x + dx, params.y + dy)
        params.x = clampedX
        params.y = clampedY
        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
    }

    private fun clampToScreenBounds(persist: Boolean) {
        val params = layoutParams ?: return
        val root = rootContainer ?: return
        if (!isAttached) return

        val (clampedX, clampedY) = computeClampedPosition(params.x, params.y)
        params.x = clampedX
        params.y = clampedY
        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Exception) {
        }
        if (persist) {
            onPositionCommitted(clampedX, clampedY)
        }
    }

    private fun snapCollapsedToNearestEdge() {
        val params = layoutParams ?: return
        val root = rootContainer ?: return
        if (!isAttached) return

        val (screenW, _) = currentScreenSize()
        val viewW = root.width.takeIf { it > 0 } ?: dp(48)
        val margin = dp(EDGE_MARGIN_DP)
        val (_, clampedY) = computeClampedPosition(params.x, params.y)
        val centerX = params.x + viewW / 2
        val targetX = if (centerX < screenW / 2) {
            margin
        } else {
            (screenW - viewW - margin).coerceAtLeast(margin)
        }

        params.y = clampedY
        snapAnimator?.cancel()
        val startX = params.x
        if (startX == targetX) {
            try {
                windowManager.updateViewLayout(root, params)
            } catch (_: Exception) {
            }
            onPositionCommitted(targetX, clampedY)
            return
        }

        snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                if (!isAttached) return@addUpdateListener
                params.x = anim.animatedValue as Int
                try {
                    windowManager.updateViewLayout(root, params)
                } catch (_: Exception) {
                }
            }
            start()
        }
        onPositionCommitted(targetX, clampedY)
    }

    private fun computeClampedPosition(rawX: Int, rawY: Int): Pair<Int, Int> {
        val (screenW, screenH) = currentScreenSize()
        val root = rootContainer
        val viewW = root?.width?.takeIf { it > 0 } ?: if (isCollapsed) dp(48) else dp(300)
        val viewH = root?.height?.takeIf { it > 0 } ?: if (isCollapsed) dp(48) else dp(96)
        val marginX = dp(EDGE_MARGIN_DP)
        val minY = dp(TOP_SAFE_MARGIN_DP)
        val maxX = (screenW - viewW - marginX).coerceAtLeast(marginX)
        val maxY = (screenH - viewH - dp(BOTTOM_SAFE_MARGIN_DP)).coerceAtLeast(minY)
        return rawX.coerceIn(marginX, maxX) to rawY.coerceIn(minY, maxY)
    }

    private fun currentScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    }

    private fun performSubtleHaptic(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    /**
     * Intercepts drag gestures anywhere on the Expanded Pill once finger movement exceeds touchSlop,
     * while letting crisp taps pass directly through to all child buttons.
     */
    private inner class DragContainerLayout(context: Context) : LinearLayout(context) {
        var onDragDelta: ((Int, Int) -> Unit)? = null
        var onDragEnd: (() -> Unit)? = null

        private var downRawX = 0f
        private var downRawY = 0f
        private var lastRawX = 0f
        private var lastRawY = 0f
        private var isDragging = false

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    lastRawX = ev.rawX
                    lastRawY = ev.rawY
                    isDragging = false
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downRawX
                    val dy = ev.rawY - downRawY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isDragging = true
                        lastRawX = ev.rawX
                        lastRawY = ev.rawY
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    return false
                }
            }
            return super.onInterceptTouchEvent(ev)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY
                    if (!isDragging && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val stepDx = (event.rawX - lastRawX).roundToInt()
                        val stepDy = (event.rawY - lastRawY).roundToInt()
                        onDragDelta?.invoke(stepDx, stepDy)
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                        onDragEnd?.invoke()
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    companion object {
        private const val CLEAR_CONFIRM_TIMEOUT_MS = 3000L
        private const val FEEDBACK_DURATION_MS = 2200L
        private const val EDGE_MARGIN_DP = 8
        private const val TOP_SAFE_MARGIN_DP = 36
        private const val BOTTOM_SAFE_MARGIN_DP = 32

        // GT Carbon & Crimson design tokens
        private val OBSIDIAN_BG = Color.parseColor("#0A0A0D")
        private val CARBON_SURFACE_94 = Color.parseColor("#F0141419")
        private val CARBON_CHIP = Color.parseColor("#1D1D24")
        private val CARBON_CHIP_PRESSED = Color.parseColor("#2A2A34")
        private val METAL_BORDER = Color.parseColor("#282830")
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
