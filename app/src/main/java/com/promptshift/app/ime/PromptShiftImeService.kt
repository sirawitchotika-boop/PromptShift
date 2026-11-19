package com.promptshift.app.ime

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.clipboard.ClipboardTrampolineActivity
import com.promptshift.app.ui.QuickDirectionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 100% Thai banking-app-safe InputMethodService (standard Android keyboard API, ZERO Accessibility).
 * Shares live SSE batch state, checkmarks, and 2,000-char quota warning with the Floating Bubble.
 */
class PromptShiftImeService : InputMethodService() {

    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var viewCollectorJob: Job? = null
    private var viewController: PromptShiftImeViewController? = null

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        styleImeWindow()

        val app = PromptShiftApp.from(this)
        val repository = app.repository
        val coordinator = app.generationCoordinator

        viewCollectorJob?.cancel()
        viewController?.destroy()
        val controller = PromptShiftImeViewController(
            context = this,
            onSwitchKeyboardClicked = { switchBackToPreviousKeyboard() },
            onAiClicked = { QuickDirectionActivity.launch(this) },
            onCancelStreamClicked = { coordinator.cancelGeneration() },
            onLoadClicked = { handleLoadFromClipboard() },
            onToggleReplaceMode = {
                val next = !repository.imePrefs.value.replaceMode
                repository.setImeReplaceMode(next)
                repository.postFeedback(if (next) "Mode: Replace" else "Mode: Append")
            },
            onToggleAutoNext = {
                val next = !repository.imePrefs.value.autoNext
                repository.setImeAutoNext(next)
                repository.postFeedback(if (next) "Auto-Next ON" else "Auto-Next OFF")
            },
            onHideKeyboardClicked = { requestHideSelf(0) },
            onPreviousClicked = { repository.stepPrevious() },
            onInsertClicked = { handleInsertCurrentPrompt() },
            onClearBoxClicked = { handleClearInputBox() },
            onNextClicked = { repository.stepNext() },
        )
        viewController = controller

        val view = controller.createView()

        viewCollectorJob = imeScope.launch {
            launch {
                repository.batchState.collect { batch ->
                    controller.renderBatchState(batch)
                }
            }
            launch {
                repository.imePrefs.collect { prefs ->
                    controller.renderImePrefs(prefs)
                }
            }
            launch {
                coordinator.generationState.collect { genState ->
                    controller.renderGenerationState(genState)
                }
            }
            launch {
                repository.feedbackEvents.collect { feedback ->
                    controller.showFeedback(feedback)
                }
            }
        }

        return view
    }

    override fun onDestroy() {
        viewController?.destroy()
        viewController = null
        imeScope.cancel()
        super.onDestroy()
    }

    private fun handleInsertCurrentPrompt() {
        val repository = PromptShiftApp.from(this).repository
        val currentPrompt = repository.batchState.value.currentPrompt ?: return
        val prefs = repository.imePrefs.value
        val ic = currentInputConnection
        val adapter = ic?.let { AndroidImeConnectionAdapter(this, it) }

        when (PromptInsertEngine.insert(currentPrompt, prefs.replaceMode, adapter)) {
            InsertOutcome.InsertedDirect,
            InsertOutcome.InsertedViaPasteFallback,
            -> {
                repository.markCurrentInserted(autoAdvance = prefs.autoNext)
            }
            InsertOutcome.CopiedFallbackOnly -> {
                repository.markCurrentCopied()
                repository.postFeedback("Copied — tap Paste", isError = false)
            }
            InsertOutcome.NoInputConnection -> {
                repository.postFeedback("Tap text box first", isError = true)
            }
        }
    }

    private fun handleClearInputBox() {
        val repository = PromptShiftApp.from(this).repository
        val ic = currentInputConnection
        val adapter = ic?.let { AndroidImeConnectionAdapter(this, it) }
        if (PromptInsertEngine.clearInputBox(adapter)) {
            repository.postFeedback("Box cleared")
        } else {
            repository.postFeedback("Tap text box first", isError = true)
        }
    }

    private fun handleLoadFromClipboard() {
        val repository = PromptShiftApp.from(this).repository
        val raw = ClipboardHelper.readPlainText(this)
        if (raw != null) {
            repository.loadFromRawText(raw)
        } else {
            // Fallback to transparent trampoline if OEM restricts background IME clipboard read
            ClipboardTrampolineActivity.launch(this)
        }
    }

    @Suppress("DEPRECATION")
    private fun switchBackToPreviousKeyboard() {
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod() || switchToNextInputMethod(false)
        } else {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            val token = window?.window?.attributes?.token
            (token != null && imm?.switchToLastInputMethod(token) == true)
        }

        if (!switched) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }
    }

    @Suppress("DEPRECATION")
    private fun styleImeWindow() {
        window?.window?.let { win ->
            win.navigationBarColor = Color.parseColor("#0A0A0D")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
            }
        }
    }
}
