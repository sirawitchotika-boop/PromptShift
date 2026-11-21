package com.promptshift.app.data

import com.promptshift.app.domain.BatchLoadResult
import com.promptshift.app.domain.BatchParseResult
import com.promptshift.app.domain.ImePrefsState
import com.promptshift.app.domain.OverlayFeedback
import com.promptshift.app.domain.OverlayUiState
import com.promptshift.app.domain.PromptBatchParser
import com.promptshift.app.domain.PromptBatchState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for prompt batch, floating overlay, and Mini-Keyboard state.
 * Shared live across Main Hub, Floating Bubble, and PromptShift Keyboard.
 */
interface PromptBatchRepository {
    val batchState: StateFlow<PromptBatchState>
    val overlayState: StateFlow<OverlayUiState>
    val imePrefs: StateFlow<ImePrefsState>
    val feedbackEvents: SharedFlow<OverlayFeedback>

    fun loadFromRawText(rawText: String?): BatchLoadResult
    fun loadParsedPrompts(prompts: List<String>): BatchLoadResult
    fun beginStreamingBatch()
    fun appendStreamedPrompt(prompt: String): Int
    fun markCurrentCopied(): String?
    fun markCurrentInserted(autoAdvance: Boolean): String?
    fun stepPrevious(): Boolean
    fun stepNext(): Boolean
    fun clearBatch()
    fun updateOverlayPosition(x: Int, y: Int)
    fun setCollapsed(collapsed: Boolean)
    fun setImeReplaceMode(replaceMode: Boolean)
    fun setImeAutoNext(autoNext: Boolean)
    fun setImeSnippetReplaceMode(replaceMode: Boolean)
    fun postFeedback(message: String, isError: Boolean = false)
}

class DefaultPromptBatchRepository(
    private val persistence: BatchPersistence,
    persistDispatcher: CoroutineDispatcher? = null,
) : PromptBatchRepository {

    private val lock = Any()
    private val feedbackSeq = AtomicLong(0L)
    private val snapshotWriter = AsyncSnapshotWriter<PersistedBatchSnapshot>(
        dispatcher = persistDispatcher,
        writeAction = { snapshot -> persistence.save(snapshot) },
    )

    private val _batchState: MutableStateFlow<PromptBatchState>
    private val _overlayState: MutableStateFlow<OverlayUiState>
    private val _imePrefs: MutableStateFlow<ImePrefsState>
    private val _feedbackEvents = MutableSharedFlow<OverlayFeedback>(extraBufferCapacity = 8)

    override val batchState: StateFlow<PromptBatchState>
    override val overlayState: StateFlow<OverlayUiState>
    override val imePrefs: StateFlow<ImePrefsState>
    override val feedbackEvents: SharedFlow<OverlayFeedback> = _feedbackEvents.asSharedFlow()

    init {
        val saved = persistence.load()
        val initialBatch = if (saved != null && saved.prompts.isNotEmpty()) {
            val clampedIndex = saved.currentIndex.coerceIn(0, saved.prompts.lastIndex)
            val validCopied = saved.copiedIndices.filter { it in saved.prompts.indices }.toSet()
            PromptBatchState(
                prompts = saved.prompts,
                currentIndex = clampedIndex,
                copiedIndices = validCopied,
            )
        } else {
            PromptBatchState()
        }

        val initialOverlay = if (saved != null) {
            OverlayUiState(
                x = saved.overlayX,
                y = saved.overlayY,
                isCollapsed = saved.isCollapsed,
                hasSavedPosition = saved.hasSavedPosition,
            )
        } else {
            OverlayUiState()
        }

        val initialImePrefs = if (saved != null) {
            ImePrefsState(
                replaceMode = saved.imeReplaceMode,
                autoNext = saved.imeAutoNext,
                snippetReplaceMode = saved.imeSnippetReplaceMode,
            )
        } else {
            ImePrefsState()
        }

        _batchState = MutableStateFlow(initialBatch)
        _overlayState = MutableStateFlow(initialOverlay)
        _imePrefs = MutableStateFlow(initialImePrefs)
        batchState = _batchState.asStateFlow()
        overlayState = _overlayState.asStateFlow()
        imePrefs = _imePrefs.asStateFlow()
    }

    override fun loadFromRawText(rawText: String?): BatchLoadResult {
        return when (val parsed = PromptBatchParser.parse(rawText)) {
            is BatchParseResult.Success -> loadParsedPrompts(parsed.prompts)
            BatchParseResult.NoDelimiterFound -> {
                postFeedback("No ===PROMPT=== found", isError = true)
                BatchLoadResult.PreservedNoDelimiter
            }
            BatchParseResult.EmptyAfterCleaning -> {
                postFeedback("No ===PROMPT=== found", isError = true)
                BatchLoadResult.PreservedEmpty
            }
        }
    }

    override fun loadParsedPrompts(prompts: List<String>): BatchLoadResult {
        val cleaned = prompts.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) {
            postFeedback("No ===PROMPT=== found", isError = true)
            return BatchLoadResult.PreservedEmpty
        }

        synchronized(lock) {
            _batchState.value = PromptBatchState(
                prompts = cleaned,
                currentIndex = 0,
                copiedIndices = emptySet(),
            )
            persistLocked()
        }
        postFeedback("Loaded ${cleaned.size}")
        return BatchLoadResult.Loaded(cleaned.size)
    }

    override fun beginStreamingBatch() {
        synchronized(lock) {
            _batchState.value = PromptBatchState()
            persistLocked()
        }
    }

    override fun appendStreamedPrompt(prompt: String): Int {
        val cleaned = prompt.trim()
        if (cleaned.isEmpty()) return _batchState.value.totalCount

        synchronized(lock) {
            val current = _batchState.value
            val updatedPrompts = current.prompts + cleaned
            _batchState.value = current.copy(
                prompts = updatedPrompts,
                currentIndex = current.currentIndex.coerceIn(0, updatedPrompts.lastIndex),
            )
            persistLocked()
            return updatedPrompts.size
        }
    }

    override fun markCurrentCopied(): String? {
        synchronized(lock) {
            val current = _batchState.value
            val prompt = current.currentPrompt ?: return null
            if (current.currentIndex !in current.copiedIndices) {
                _batchState.value = current.copy(
                    copiedIndices = current.copiedIndices + current.currentIndex,
                )
                persistLocked()
            }
            return prompt
        }
    }

    override fun markCurrentInserted(autoAdvance: Boolean): String? {
        synchronized(lock) {
            val current = _batchState.value
            val prompt = current.currentPrompt ?: return null
            val updatedCopied = current.copiedIndices + current.currentIndex
            val nextIndex = if (autoAdvance && current.canGoNext) {
                current.currentIndex + 1
            } else {
                current.currentIndex
            }
            if (updatedCopied != current.copiedIndices || nextIndex != current.currentIndex) {
                _batchState.value = current.copy(
                    currentIndex = nextIndex,
                    copiedIndices = updatedCopied,
                )
                persistLocked()
            }
            return prompt
        }
    }

    override fun stepPrevious(): Boolean {
        synchronized(lock) {
            val current = _batchState.value
            if (!current.canGoPrevious) return false
            _batchState.value = current.copy(currentIndex = current.currentIndex - 1)
            persistLocked()
            return true
        }
    }

    override fun stepNext(): Boolean {
        synchronized(lock) {
            val current = _batchState.value
            if (!current.canGoNext) return false
            _batchState.value = current.copy(currentIndex = current.currentIndex + 1)
            persistLocked()
            return true
        }
    }

    override fun clearBatch() {
        synchronized(lock) {
            _batchState.value = PromptBatchState()
            persistLocked()
        }
        postFeedback("Batch cleared")
    }

    override fun updateOverlayPosition(x: Int, y: Int) {
        synchronized(lock) {
            _overlayState.value = _overlayState.value.copy(
                x = x,
                y = y,
                hasSavedPosition = true,
            )
            persistLocked()
        }
    }

    override fun setCollapsed(collapsed: Boolean) {
        synchronized(lock) {
            if (_overlayState.value.isCollapsed == collapsed) return
            _overlayState.value = _overlayState.value.copy(isCollapsed = collapsed)
            persistLocked()
        }
    }

    override fun setImeReplaceMode(replaceMode: Boolean) {
        synchronized(lock) {
            if (_imePrefs.value.replaceMode == replaceMode) return
            _imePrefs.value = _imePrefs.value.copy(replaceMode = replaceMode)
            persistLocked()
        }
    }

    override fun setImeAutoNext(autoNext: Boolean) {
        synchronized(lock) {
            if (_imePrefs.value.autoNext == autoNext) return
            _imePrefs.value = _imePrefs.value.copy(autoNext = autoNext)
            persistLocked()
        }
    }

    override fun setImeSnippetReplaceMode(replaceMode: Boolean) {
        synchronized(lock) {
            if (_imePrefs.value.snippetReplaceMode == replaceMode) return
            _imePrefs.value = _imePrefs.value.copy(snippetReplaceMode = replaceMode)
            persistLocked()
        }
    }

    override fun postFeedback(message: String, isError: Boolean) {
        _feedbackEvents.tryEmit(
            OverlayFeedback(
                id = feedbackSeq.incrementAndGet(),
                message = message,
                isError = isError,
            ),
        )
    }

    private fun persistLocked() {
        val batch = _batchState.value
        val overlay = _overlayState.value
        val ime = _imePrefs.value
        snapshotWriter.enqueue(
            PersistedBatchSnapshot(
                prompts = batch.prompts,
                currentIndex = batch.currentIndex,
                copiedIndices = batch.copiedIndices,
                overlayX = overlay.x,
                overlayY = overlay.y,
                hasSavedPosition = overlay.hasSavedPosition,
                isCollapsed = overlay.isCollapsed,
                imeReplaceMode = ime.replaceMode,
                imeAutoNext = ime.autoNext,
                imeSnippetReplaceMode = ime.snippetReplaceMode,
            ),
        )
    }
}
