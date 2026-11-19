package com.promptshift.app.clipboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.promptshift.app.PromptShiftApp

/**
 * 1-frame transparent, no-animation focus trampoline for Android 10+ background clipboard read.
 * Launches invisibly over the current app (e.g. SeaArt), reads raw ClipboardManager text
 * as soon as window focus arrives, dispatches it to PromptBatchRepository, and finishes immediately.
 */
class ClipboardTrampolineActivity : Activity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var handled = false

    private val safetyTimeout = Runnable {
        if (!handled) {
            handled = true
            PromptShiftApp.from(this).repository.postFeedback(
                message = "Clipboard unavailable",
                isError = true,
            )
            finishSilently()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suppressTransitions()
        mainHandler.postDelayed(safetyTimeout, SAFETY_TIMEOUT_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !handled) {
            handled = true
            mainHandler.removeCallbacks(safetyTimeout)
            window.decorView.post {
                val rawText = ClipboardHelper.readPlainText(this)
                PromptShiftApp.from(this).repository.loadFromRawText(rawText)
                finishSilently()
            }
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(safetyTimeout)
        super.onDestroy()
    }

    private fun finishSilently() {
        if (!isFinishing) {
            finish()
        }
        suppressTransitions()
    }

    @Suppress("DEPRECATION")
    private fun suppressTransitions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val SAFETY_TIMEOUT_MS = 1200L

        fun launch(context: Context) {
            val intent = Intent(context, ClipboardTrampolineActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
            }
            context.startActivity(intent)
        }
    }
}
