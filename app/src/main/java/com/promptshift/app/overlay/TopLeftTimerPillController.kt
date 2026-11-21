package com.promptshift.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.promptshift.app.timer.TimerState
import kotlin.math.abs

/**
 * Compact draggable floating countdown pill anchored by default at the top-center of the screen
 * (away from top-left Back buttons and top-right actions) while a timer is running outside PromptShift.
 * Uses the existing Display over other apps permission (ZERO Accessibility).
 */
class TopLeftTimerPillController(
    private val context: Context,
    private val onPillClicked: () -> Unit,
    private val onPositionSaved: (x: Int, y: Int) -> Unit,
) {
    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var pillView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAttached: Boolean = false
    private var lastRenderedText: String? = null
    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    fun update(
        timerState: TimerState,
        shouldShow: Boolean,
        savedX: Int?,
        savedY: Int?,
    ) {
        if (!shouldShow || !timerState.isRunning || !Settings.canDrawOverlays(context)) {
            detach()
            return
        }

        if (!isAttached) {
            attach(savedX = savedX, savedY = savedY)
        } else {
            val wm = windowManager
            if (wm != null) {
                val currentWidth = resolveScreenWidthPx(wm)
                val currentHeight = resolveScreenHeightPx(wm)
                if (currentWidth != lastScreenWidth || currentHeight != lastScreenHeight) {
                    reclampToScreenBounds()
                }
            }
        }

        val nextText = formatPillText(timerState.formattedRemaining())
        if (nextText != lastRenderedText) {
            pillView?.text = nextText
            lastRenderedText = nextText
        }
    }

    fun onConfigurationChanged() {
        val view = pillView ?: return
        if (!isAttached) return
        view.post {
            reclampToScreenBounds()
        }
    }

    fun detach() {
        val view = pillView
        if (isAttached && view != null) {
            try {
                windowManager?.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    windowManager?.removeView(view)
                } catch (_: Exception) {
                }
            }
        }
        isAttached = false
        pillView = null
        layoutParams = null
        lastRenderedText = null
        lastScreenWidth = 0
        lastScreenHeight = 0
    }

    private fun reclampToScreenBounds() {
        val wm = windowManager ?: return
        val view = pillView ?: return
        val lp = layoutParams ?: return
        if (!isAttached) return

        val screenWidth = resolveScreenWidthPx(wm)
        val screenHeight = resolveScreenHeightPx(wm)
        lastScreenWidth = screenWidth
        lastScreenHeight = screenHeight

        val (clampedX, clampedY) = clampPosition(
            x = lp.x,
            y = lp.y,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pillWidth = view.width.coerceAtLeast(dp(60)),
            pillHeight = view.height.coerceAtLeast(dp(36)),
        )
        if (clampedX != lp.x || clampedY != lp.y) {
            lp.x = clampedX
            lp.y = clampedY
            try {
                wm.updateViewLayout(view, lp)
            } catch (e: Exception) {
                Log.w(TAG, "Unable to re-clamp timer pill after rotation", e)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach(savedX: Int?, savedY: Int?) {
        val wm = windowManager ?: return
        if (!Settings.canDrawOverlays(context)) return

        val screenWidth = resolveScreenWidthPx(wm)
        val screenHeight = resolveScreenHeightPx(wm)
        val topInset = resolveTopInsetPx(wm)
        val estimatedPillWidth = dp(86)
        val defaultX = ((screenWidth - estimatedPillWidth) / 2).coerceAtLeast(dp(8))
        val defaultY = topInset + dp(4)

        val (initialX, initialY) = clampPosition(
            x = savedX ?: defaultX,
            y = savedY ?: defaultY,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            pillWidth = dp(60),
            pillHeight = dp(40),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var isDragging = false

        val initialText = formatPillText("00:00")
        val view = TextView(context).apply {
            text = initialText
            setTextColor(Color.parseColor("#34D399"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(dp(10), dp(5), dp(10), dp(5))
            elevation = dp(6).toFloat()
            contentDescription = "Countdown timer"
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(999).toFloat()
                setColor(Color.parseColor("#EB14161A"))
                setStroke(dp(1), Color.parseColor("#B334D399"))
            }
            setOnTouchListener { v, event ->
                val lp = params
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downRawX = event.rawX
                        downRawY = event.rawY
                        startX = lp.x
                        startY = lp.y
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downRawX).toInt()
                        val dy = (event.rawY - downRawY).toInt()
                        if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val currentWidth = resolveScreenWidthPx(wm)
                            val currentHeight = resolveScreenHeightPx(wm)
                            lastScreenWidth = currentWidth
                            lastScreenHeight = currentHeight
                            val (clampedX, clampedY) = clampPosition(
                                x = startX + dx,
                                y = startY + dy,
                                screenWidth = currentWidth,
                                screenHeight = currentHeight,
                                pillWidth = v.width.coerceAtLeast(dp(60)),
                                pillHeight = v.height.coerceAtLeast(dp(36)),
                            )
                            lp.x = clampedX
                            lp.y = clampedY
                            try {
                                wm.updateViewLayout(v, lp)
                            } catch (e: Exception) {
                                Log.w(TAG, "Unable to update timer pill layout", e)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isDragging) {
                            onPositionSaved(lp.x, lp.y)
                        } else {
                            onPillClicked()
                        }
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }
        }

        try {
            wm.addView(view, params)
            pillView = view
            layoutParams = params
            isAttached = true
            lastRenderedText = initialText
            lastScreenWidth = screenWidth
            lastScreenHeight = screenHeight
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach floating timer pill", e)
            pillView = null
            layoutParams = null
            isAttached = false
            lastRenderedText = null
            lastScreenWidth = 0
            lastScreenHeight = 0
        }
    }

    private fun resolveScreenWidthPx(wm: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                wm.currentWindowMetrics.bounds.width()
            } catch (_: Exception) {
                context.resources.displayMetrics.widthPixels
            }
        } else {
            context.resources.displayMetrics.widthPixels
        }
    }

    private fun resolveScreenHeightPx(wm: WindowManager): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                wm.currentWindowMetrics.bounds.height()
            } catch (_: Exception) {
                context.resources.displayMetrics.heightPixels
            }
        } else {
            context.resources.displayMetrics.heightPixels
        }
    }

    private fun resolveTopInsetPx(wm: WindowManager): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val insets = wm.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())
                if (insets.top > 0) return insets.top
            } catch (_: Exception) {
            }
        }
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) {
            context.resources.getDimensionPixelSize(resId)
        } else {
            dp(28)
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()

    companion object {
        private const val TAG = "PromptShiftTimerPill"

        internal fun formatPillText(formattedRemaining: String): String =
            "⏱ $formattedRemaining"

        internal fun clampPosition(
            x: Int,
            y: Int,
            screenWidth: Int,
            screenHeight: Int,
            pillWidth: Int,
            pillHeight: Int,
        ): Pair<Int, Int> {
            val maxX = (screenWidth - pillWidth).coerceAtLeast(0)
            val maxY = (screenHeight - pillHeight).coerceAtLeast(0)
            return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
        }
    }
}
