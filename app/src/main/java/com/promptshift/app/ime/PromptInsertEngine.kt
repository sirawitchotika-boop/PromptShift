package com.promptshift.app.ime

import android.content.Context
import android.view.inputmethod.InputConnection
import com.promptshift.app.clipboard.ClipboardHelper

enum class InsertOutcome {
    InsertedDirect,
    InsertedViaPasteFallback,
    CopiedFallbackOnly,
    NoInputConnection,
}

/**
 * Pure abstraction over Android's InputConnection + Clipboard fallback so all auto-insert
 * and WebView fallback paths are 100% JVM unit-testable.
 */
interface ImeConnectionAdapter {
    fun beginBatchEdit()
    fun endBatchEdit()
    fun performSelectAll(): Boolean
    fun performPaste(): Boolean
    fun commitText(text: String): Boolean
    fun getTextBeforeCursorLength(maxChars: Int): Int
    fun getTextAfterCursorLength(maxChars: Int): Int
    fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean
    fun writeClipboard(text: String): Boolean
}

class AndroidImeConnectionAdapter(
    private val context: Context,
    private val inputConnection: InputConnection,
) : ImeConnectionAdapter {

    override fun beginBatchEdit() {
        inputConnection.beginBatchEdit()
    }

    override fun endBatchEdit() {
        inputConnection.endBatchEdit()
    }

    override fun performSelectAll(): Boolean =
        inputConnection.performContextMenuAction(android.R.id.selectAll)

    override fun performPaste(): Boolean =
        inputConnection.performContextMenuAction(android.R.id.paste)

    override fun commitText(text: String): Boolean =
        inputConnection.commitText(text, 1)

    override fun getTextBeforeCursorLength(maxChars: Int): Int =
        inputConnection.getTextBeforeCursor(maxChars, 0)?.length ?: 0

    override fun getTextAfterCursorLength(maxChars: Int): Int =
        inputConnection.getTextAfterCursor(maxChars, 0)?.length ?: 0

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
        inputConnection.deleteSurroundingText(beforeLength, afterLength)

    override fun writeClipboard(text: String): Boolean =
        ClipboardHelper.writePlainText(context, "PromptShift", text)
}

/**
 * Banking-app-safe text insertion engine for PromptShift Mini-Keyboard.
 * Handles native EditText, Compose TextField, and WebViews (with automatic clipboard-paste fallback).
 */
object PromptInsertEngine {

    private const val MAX_CLEAR_SCAN = 25_000

    fun insert(
        prompt: String,
        replaceExisting: Boolean,
        adapter: ImeConnectionAdapter?,
    ): InsertOutcome {
        if (adapter == null) return InsertOutcome.NoInputConnection

        adapter.beginBatchEdit()
        return try {
            if (replaceExisting) {
                val selectedAll = adapter.performSelectAll()
                if (!selectedAll) {
                    clearViaSurroundingDelete(adapter)
                }
            }

            if (adapter.commitText(prompt)) {
                InsertOutcome.InsertedDirect
            } else {
                // Automatic WebView fallback: write to system clipboard and trigger standard Paste
                adapter.writeClipboard(prompt)
                if (adapter.performPaste()) {
                    InsertOutcome.InsertedViaPasteFallback
                } else {
                    InsertOutcome.CopiedFallbackOnly
                }
            }
        } finally {
            adapter.endBatchEdit()
        }
    }

    fun clearInputBox(adapter: ImeConnectionAdapter?): Boolean {
        if (adapter == null) return false
        adapter.beginBatchEdit()
        return try {
            if (adapter.performSelectAll() && adapter.commitText("")) {
                true
            } else {
                clearViaSurroundingDelete(adapter)
            }
        } finally {
            adapter.endBatchEdit()
        }
    }

    private fun clearViaSurroundingDelete(adapter: ImeConnectionAdapter): Boolean {
        val before = adapter.getTextBeforeCursorLength(MAX_CLEAR_SCAN)
        val after = adapter.getTextAfterCursorLength(MAX_CLEAR_SCAN)
        return if (before > 0 || after > 0) {
            adapter.deleteSurroundingText(before, after)
        } else {
            true
        }
    }
}
