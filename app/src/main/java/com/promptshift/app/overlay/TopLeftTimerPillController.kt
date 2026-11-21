package com.promptshift.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.promptshift.app.timer.TimerState

/**
 * Compact floating countdown pill anchored at the top-left of the screen while
 * a timer is running outside PromptShift. Uses the existing Display over other apps
 * permission (ZERO Accessibility).
 */
class TopLeftTimerPillController(
    private val context: Context,
    private val onPillClicked: () -> Unit,
) {
    private val windowManager: WindowManager? =
        context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager

    private var pillView: TextView? = null
    private var isAttached: Boolean = false

    fun update(timerState: TimerState, shouldShow: Boolean) {
        if (!shouldShow || !timerState.isRunning || !Settings.canDrawOverlays(context)) {
            detach()
            return
        }

        if (!isAttached) {
            attach()
        }
        pillView?.text = "⏱ ${timerState.formattedRemaining()}"
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
    }

    private fun attach() {
        val wm = windowManager ?: return
        if (!Settings.canDrawOverlays(context)) return

        val view = TextView(context).apply {
            text = "⏱ 00:00"
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
            setOnClickListener { onPillClicked() }
        }

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
            x = dp(10)
            y = resolveTopInsetPx(wm) + dp(6)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            wm.addView(view, params)
            pillView = view
            isAttached = true
        } catch (_: Exception) {
            pillView = null
            isAttached = false
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
}
