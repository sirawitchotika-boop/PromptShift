package com.promptshift.app.ime

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.promptshift.app.PromptShiftApp
import com.promptshift.app.clipboard.ClipboardHelper
import com.promptshift.app.clipboard.ClipboardTrampolineActivity
import com.promptshift.app.domain.PromptSnippet
import com.promptshift.app.domain.PromptTextComposer
import com.promptshift.app.ui.QuickDirectionActivity
import com.promptshift.app.ui.RealmeReadinessHelper
import com.promptshift.app.ui.TimerPopupActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * 100% Thai banking-app-safe InputMethodService (standard Android keyboard API, ZERO Accessibility).
 * Shares live SSE batch state, Trigger Words, Snippets, Recent 10, Timer, and 2,000-char quota warning.
 */
class PromptShiftImeService : InputMethodService() {

    private val imeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var viewCollectorJob: Job? = null
    private var viewController: PromptShiftImeViewController? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = WeakReference(this)
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        activeInstance = WeakReference(this)
        styleImeWindow()

        val app = PromptShiftApp.from(this)
        val repository = app.repository
        val configRepo = app.aiConfigRepository
        val coordinator = app.generationCoordinator
        val timerManager = app.timerManager

        viewCollectorJob?.cancel()
        viewController?.destroy()
        val controller = PromptShiftImeViewController(
            context = this,
            onSwitchKeyboardClicked = { switchBackToPreviousKeyboard() },
            onAiClicked = { QuickDirectionActivity.launch(this) },
            onCancelStreamClicked = { coordinator.cancelGeneration() },
            onLoadClicked = { handleLoadFromClipboard() },
            onTimerClicked = { TimerPopupActivity.launch(this) },
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
            onToggleSnippetReplaceMode = {
                val next = !repository.imePrefs.value.snippetReplaceMode
                repository.setImeSnippetReplaceMode(next)
                repository.postFeedback(if (next) "Snip: Replace" else "Snip: Join")
            },
            onSnippetClicked = { snippet -> handleInsertSnippet(snippet) },
            onRecentClicked = { recent -> handleInsertRecent(recent) },
            onSaveCurrentSlotClicked = { handleSaveCurrentSlotAsSnippet() },
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
                configRepo.configState.collect { config ->
                    controller.renderAiConfig(config)
                }
            }
            launch {
                coordinator.generationState.collect { genState ->
                    controller.renderGenerationState(genState)
                }
            }
            launch {
                timerManager.state.collect { timerState ->
                    controller.renderTimerState(timerState)
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
        if (activeInstance?.get() === this) {
            activeInstance = null
        }
        viewController?.destroy()
        viewController = null
        imeScope.cancel()
        super.onDestroy()
    }

    private fun handleInsertCurrentPrompt() {
        val app = PromptShiftApp.from(this)
        val repository = app.repository
        val configRepo = app.aiConfigRepository
        val rawPrompt = repository.batchState.value.currentPrompt ?: return
        val finalPrompt = PromptTextComposer.applyTriggerWord(
            rawPrompt,
            configRepo.configState.value.effectiveTriggerWord,
        )
        val prefs = repository.imePrefs.value
        val ic = currentInputConnection
        val adapter = ic?.let { AndroidImeConnectionAdapter(this, it) }

        when (PromptInsertEngine.insert(finalPrompt, prefs.replaceMode, adapter)) {
            InsertOutcome.InsertedDirect,
            InsertOutcome.InsertedViaPasteFallback,
            -> {
                repository.markCurrentInserted(autoAdvance = prefs.autoNext)
                configRepo.recordRecentPrompt(finalPrompt)
            }
            InsertOutcome.CopiedFallbackOnly -> {
                repository.markCurrentCopied()
                configRepo.recordRecentPrompt(finalPrompt)
                repository.postFeedback("Copied — tap Paste", isError = false)
            }
            InsertOutcome.NoInputConnection -> {
                repository.postFeedback("Tap text box first", isError = true)
            }
        }
    }

    private fun handleInsertSnippet(snippet: PromptSnippet) {
        val app = PromptShiftApp.from(this)
        val repository = app.repository
        val configRepo = app.aiConfigRepository
        val resolved = if (snippet.includeTriggerWord) {
            PromptTextComposer.applyTriggerWord(
                snippet.body,
                configRepo.configState.value.effectiveTriggerWord,
            )
        } else {
            snippet.body.trim()
        }
        val replaceMode = repository.imePrefs.value.snippetReplaceMode
        val ic = currentInputConnection
        val adapter = ic?.let { AndroidImeConnectionAdapter(this, it) }

        when (PromptInsertEngine.insertSnippet(resolved, replaceMode, adapter)) {
            InsertOutcome.InsertedDirect,
            InsertOutcome.InsertedViaPasteFallback,
            -> {
                configRepo.recordRecentPrompt(resolved)
                repository.postFeedback("Inserted: ${snippet.title.take(12)}")
            }
            InsertOutcome.CopiedFallbackOnly -> {
                configRepo.recordRecentPrompt(resolved)
                repository.postFeedback("Copied — tap Paste", isError = false)
            }
            InsertOutcome.NoInputConnection -> {
                repository.postFeedback("Tap text box first", isError = true)
            }
        }
    }

    private fun handleInsertRecent(recentText: String) {
        val app = PromptShiftApp.from(this)
        val repository = app.repository
        val replaceMode = repository.imePrefs.value.snippetReplaceMode
        val ic = currentInputConnection
        val adapter = ic?.let { AndroidImeConnectionAdapter(this, it) }

        when (PromptInsertEngine.insertSnippet(recentText, replaceMode, adapter)) {
            InsertOutcome.InsertedDirect,
            InsertOutcome.InsertedViaPasteFallback,
            -> {
                app.aiConfigRepository.recordRecentPrompt(recentText)
                repository.postFeedback("Inserted recent ✅")
            }
            InsertOutcome.CopiedFallbackOnly -> {
                repository.postFeedback("Copied — tap Paste", isError = false)
            }
            InsertOutcome.NoInputConnection -> {
                repository.postFeedback("Tap text box first", isError = true)
            }
        }
    }

    private fun handleSaveCurrentSlotAsSnippet() {
        val app = PromptShiftApp.from(this)
        val batch = app.repository.batchState.value
        val currentPrompt = batch.currentPrompt ?: return
        val autoTitle = currentPrompt.trim()
            .replace('\n', ' ')
            .take(24)
            .ifBlank { "Slot #${batch.displayIndex}" }
        app.aiConfigRepository.saveSnippet(
            id = null,
            title = autoTitle,
            body = currentPrompt,
            includeTriggerWord = true,
        )
        app.repository.postFeedback("Saved snippet 📌")
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

    private fun switchToTargetOrPreviousKeyboard(preferredImeId: String?): Boolean {
        val targetId = preferredImeId?.trim()?.takeIf { it.isNotEmpty() }
        if (targetId != null) {
            val enabledOthers = RealmeReadinessHelper.getOtherEnabledKeyboards(this)
            if (enabledOthers.any { it.id == targetId }) {
                try {
                    switchInputMethod(targetId)
                    return true
                } catch (e: Exception) {
                    Log.w("PromptShiftIme", "Failed to switch to preferred IME $targetId; falling back", e)
                }
            }
        }
        switchBackToPreviousKeyboard()
        return true
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

    companion object {
        @Volatile
        private var activeInstance: WeakReference<PromptShiftImeService>? = null

        fun switchKeyboardAfterTimerPopup(context: Context, preferredImeId: String?): Boolean {
            if (!RealmeReadinessHelper.isPromptShiftKeyboardActive(context)) {
                return false
            }
            val service = activeInstance?.get() ?: return false
            return service.switchToTargetOrPreviousKeyboard(preferredImeId)
        }
    }
}
