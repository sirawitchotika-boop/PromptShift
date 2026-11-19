package com.promptshift.app.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptInsertEngineTest {

    private class FakeImeAdapter(
        var selectAllSupported: Boolean = true,
        var commitTextSupported: Boolean = true,
        var pasteSupported: Boolean = true,
        var beforeCursorLen: Int = 0,
        var afterCursorLen: Int = 0,
    ) : ImeConnectionAdapter {
        val ops = mutableListOf<String>()
        var clipboardText: String? = null
        var committedText: String? = null
        var deletedBefore = 0
        var deletedAfter = 0
        var batchBalance = 0

        override fun beginBatchEdit() {
            batchBalance++
            ops.add("begin")
        }

        override fun endBatchEdit() {
            batchBalance--
            ops.add("end")
        }

        override fun performSelectAll(): Boolean {
            ops.add("selectAll:$selectAllSupported")
            return selectAllSupported
        }

        override fun performPaste(): Boolean {
            ops.add("paste:$pasteSupported")
            return pasteSupported
        }

        override fun commitText(text: String): Boolean {
            ops.add("commit:${text.length}:$commitTextSupported")
            if (commitTextSupported) committedText = text
            return commitTextSupported
        }

        override fun getTextBeforeCursorLength(maxChars: Int): Int = beforeCursorLen
        override fun getTextAfterCursorLength(maxChars: Int): Int = afterCursorLen

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletedBefore = beforeLength
            deletedAfter = afterLength
            ops.add("delete:$beforeLength:$afterLength")
            return true
        }

        override fun writeClipboard(text: String): Boolean {
            clipboardText = text
            ops.add("clipboard:${text.length}")
            return true
        }
    }

    @Test
    fun `replace mode uses selectAll then commitText directly for full 2500 char prompt`() {
        val longPrompt = "A".repeat(2500)
        val adapter = FakeImeAdapter(selectAllSupported = true, commitTextSupported = true)

        val outcome = PromptInsertEngine.insert(longPrompt, replaceExisting = true, adapter = adapter)

        assertEquals(InsertOutcome.InsertedDirect, outcome)
        assertEquals(longPrompt, adapter.committedText)
        assertEquals(0, adapter.batchBalance)
        assertEquals(listOf("begin", "selectAll:true", "commit:2500:true", "end"), adapter.ops)
    }

    @Test
    fun `replace mode falls back to deleteSurroundingText when selectAll is unsupported`() {
        val adapter = FakeImeAdapter(
            selectAllSupported = false,
            commitTextSupported = true,
            beforeCursorLen = 120,
            afterCursorLen = 45,
        )

        val outcome = PromptInsertEngine.insert("Fresh prompt", replaceExisting = true, adapter = adapter)

        assertEquals(InsertOutcome.InsertedDirect, outcome)
        assertEquals(120, adapter.deletedBefore)
        assertEquals(45, adapter.deletedAfter)
        assertEquals("Fresh prompt", adapter.committedText)
        assertEquals(0, adapter.batchBalance)
    }

    @Test
    fun `append mode skips clearing and commits right at cursor`() {
        val adapter = FakeImeAdapter(selectAllSupported = true, commitTextSupported = true)

        val outcome = PromptInsertEngine.insert(", volumetric light", replaceExisting = false, adapter = adapter)

        assertEquals(InsertOutcome.InsertedDirect, outcome)
        assertEquals(", volumetric light", adapter.committedText)
        assertEquals(listOf("begin", "commit:18:true", "end"), adapter.ops)
    }

    @Test
    fun `strict WebView commitText failure automatically falls back to clipboard plus paste`() {
        val adapter = FakeImeAdapter(
            selectAllSupported = true,
            commitTextSupported = false,
            pasteSupported = true,
        )

        val outcome = PromptInsertEngine.insert("WebView prompt", replaceExisting = true, adapter = adapter)

        assertEquals(InsertOutcome.InsertedViaPasteFallback, outcome)
        assertEquals("WebView prompt", adapter.clipboardText)
        assertEquals(
            listOf("begin", "selectAll:true", "commit:14:false", "clipboard:14", "paste:true", "end"),
            adapter.ops,
        )
        assertEquals(0, adapter.batchBalance)
    }

    @Test
    fun `returns CopiedFallbackOnly if both commitText and paste fail, and NoInputConnection when null`() {
        val adapter = FakeImeAdapter(commitTextSupported = false, pasteSupported = false)
        assertEquals(
            InsertOutcome.CopiedFallbackOnly,
            PromptInsertEngine.insert("Saved to clipboard", replaceExisting = false, adapter = adapter),
        )
        assertEquals("Saved to clipboard", adapter.clipboardText)

        assertEquals(
            InsertOutcome.NoInputConnection,
            PromptInsertEngine.insert("No box", replaceExisting = true, adapter = null),
        )
    }

    @Test
    fun `clearInputBox clears via selectAll or surrounding delete fallback`() {
        val directAdapter = FakeImeAdapter(selectAllSupported = true, commitTextSupported = true)
        assertTrue(PromptInsertEngine.clearInputBox(directAdapter))
        assertEquals("", directAdapter.committedText)

        val fallbackAdapter = FakeImeAdapter(selectAllSupported = false, beforeCursorLen = 50, afterCursorLen = 10)
        assertTrue(PromptInsertEngine.clearInputBox(fallbackAdapter))
        assertEquals(50, fallbackAdapter.deletedBefore)
        assertEquals(10, fallbackAdapter.deletedAfter)
    }
}
