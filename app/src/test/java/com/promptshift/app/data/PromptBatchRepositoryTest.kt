package com.promptshift.app.data

import com.promptshift.app.domain.BatchLoadResult
import com.promptshift.app.domain.PromptFormatGuide
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PromptBatchRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class InMemoryBatchPersistence : BatchPersistence {
        var snapshot: PersistedBatchSnapshot? = null
        override fun load(): PersistedBatchSnapshot? = snapshot
        override fun save(snapshot: PersistedBatchSnapshot) {
            this.snapshot = snapshot
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `accidental overwrite guard preserves existing batch, index, and checkmarks`() = runTest {
        val repo = DefaultPromptBatchRepository(InMemoryBatchPersistence())
        val validBatch = "===PROMPT===\nPrompt A\n===PROMPT===\nPrompt B\n===PROMPT===\nPrompt C"

        assertEquals(BatchLoadResult.Loaded(3), repo.loadFromRawText(validBatch))
        assertTrue(repo.stepNext()) // now on index 1 (#2/3)
        assertEquals("Prompt B", repo.markCurrentCopied())

        var latestFeedbackMessage: String? = null
        var latestFeedbackIsError = false
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            val event = repo.feedbackEvents.first()
            latestFeedbackMessage = event.message
            latestFeedbackIsError = event.isError
        }

        val badResult = repo.loadFromRawText("https://seaart.ai/some-url-without-delimiter")
        job.join()

        assertEquals(BatchLoadResult.PreservedNoDelimiter, badResult)
        assertEquals("No ===PROMPT=== found", latestFeedbackMessage)
        assertTrue(latestFeedbackIsError)

        // Existing batch must be completely untouched
        val state = repo.batchState.value
        assertEquals(listOf("Prompt A", "Prompt B", "Prompt C"), state.prompts)
        assertEquals(1, state.currentIndex)
        assertEquals(setOf(1), state.copiedIndices)

        // Empty delimiter-only payload must also preserve batch
        assertEquals(
            BatchLoadResult.PreservedEmpty,
            repo.loadFromRawText("===PROMPT===\n   \n===PROMPT==="),
        )
        assertEquals(3, repo.batchState.value.totalCount)
        assertEquals(1, repo.batchState.value.currentIndex)
    }

    @Test
    fun `2000 char soft quota counts cleaned prompt strictly and never blocks copy`() {
        val repo = DefaultPromptBatchRepository(InMemoryBatchPersistence())
        val exact2000 = "a".repeat(PromptFormatGuide.SOFT_QUOTA_CHARS)
        val over2001 = "b".repeat(PromptFormatGuide.SOFT_QUOTA_CHARS + 1)

        val raw = "===PROMPT===\n  $exact2000  \n===PROMPT===\n$over2001"
        assertEquals(BatchLoadResult.Loaded(2), repo.loadFromRawText(raw))

        // Slot #1: exactly 2,000 chars -> NOT exceeding quota
        val slot1 = repo.batchState.value
        assertEquals(2000, slot1.currentCharCount)
        assertEquals("2000/2000", slot1.charCountLabel())
        assertFalse(slot1.isCurrentExceedingQuota)

        // Slot #2: 2,001 chars -> warning flag ON, but copy still succeeds with full text
        assertTrue(repo.stepNext())
        val slot2 = repo.batchState.value
        assertEquals(2001, slot2.currentCharCount)
        assertEquals("2001/2000", slot2.charCountLabel())
        assertTrue(slot2.isCurrentExceedingQuota)

        val copiedText = repo.markCurrentCopied()
        assertEquals(over2001, copiedText)
        assertEquals(2001, copiedText?.length)
        assertTrue(repo.batchState.value.isCurrentCopied)
    }

    @Test
    fun `copy stays on current slot, manual stepping has no wrap-around, and checkmarks persist`() {
        val repo = DefaultPromptBatchRepository(InMemoryBatchPersistence())
        repo.loadFromRawText("===PROMPT===\nOne\n===PROMPT===\nTwo\n===PROMPT===\nThree")

        assertEquals("Copy #1/3", repo.batchState.value.copyButtonLabel())
        assertFalse(repo.stepPrevious()) // no wrap-around below #1
        assertEquals(0, repo.batchState.value.currentIndex)

        // Copy #1/3 -> stays on #1/3 and turns into Copied ✅ #1/3
        assertEquals("One", repo.markCurrentCopied())
        assertEquals(0, repo.batchState.value.currentIndex)
        assertEquals("Copied ✅ #1/3", repo.batchState.value.copyButtonLabel())

        // Manually advance to #2/3 (not copied yet)
        assertTrue(repo.stepNext())
        assertEquals(1, repo.batchState.value.currentIndex)
        assertEquals("Copy #2/3", repo.batchState.value.copyButtonLabel())

        // Manually advance to #3/3 and copy it
        assertTrue(repo.stepNext())
        assertEquals("Three", repo.markCurrentCopied())
        assertEquals("Copied ✅ #3/3", repo.batchState.value.copyButtonLabel())
        assertFalse(repo.stepNext()) // no wrap-around past #N
        assertEquals(2, repo.batchState.value.currentIndex)

        // Step back to #1/3 -> checkmark memory preserved
        assertTrue(repo.stepPrevious())
        assertFalse(repo.batchState.value.isCurrentCopied)
        assertTrue(repo.stepPrevious())
        assertTrue(repo.batchState.value.isCurrentCopied)
        assertEquals(2, repo.batchState.value.copiedCount)

        // Clear batch resets everything cleanly
        repo.clearBatch()
        assertTrue(repo.batchState.value.isEmpty)
        assertEquals("Tap Load", repo.batchState.value.copyButtonLabel())
        assertEquals("•", repo.batchState.value.collapsedBadgeLabel())
        assertNull(repo.markCurrentCopied())
    }

    @Test
    fun `file persistence restores prompts, checkmarks, index, and overlay geometry across restarts`() {
        val stateFile = File(tempFolder.root, "promptshift_state.json")
        val persistence = FileBatchPersistence(stateFile)
        val firstSession = DefaultPromptBatchRepository(persistence)

        val specialPrompt = "Hero \"GT\" \\path\\to\\file\nLine2\tTabbed ไทย 🔥 " + "x".repeat(2500)
        val batch = (1..22).joinToString("\n") { i ->
            "===PROMPT===\n" + if (i == 1) specialPrompt else "Prompt #$i " + "y".repeat(2100)
        }
        assertTrue("Batch must exceed 45,000 chars", batch.length > 45_000)

        assertEquals(BatchLoadResult.Loaded(22), firstSession.loadFromRawText(batch))
        firstSession.markCurrentCopied() // index 0 copied
        firstSession.stepNext() // index 1
        firstSession.stepNext() // index 2
        firstSession.markCurrentCopied() // index 2 copied
        firstSession.updateOverlayPosition(x = 42, y = 640)
        firstSession.setCollapsed(true)

        // Simulate full app close & cold restart using the same persisted file
        val restartedSession = DefaultPromptBatchRepository(FileBatchPersistence(stateFile))
        val restoredBatch = restartedSession.batchState.value
        val restoredOverlay = restartedSession.overlayState.value

        assertEquals(22, restoredBatch.totalCount)
        assertEquals(specialPrompt, restoredBatch.prompts[0])
        assertEquals(2, restoredBatch.currentIndex)
        assertEquals(setOf(0, 2), restoredBatch.copiedIndices)
        assertTrue(restoredBatch.isCurrentCopied)

        assertEquals(42, restoredOverlay.x)
        assertEquals(640, restoredOverlay.y)
        assertTrue(restoredOverlay.hasSavedPosition)
        assertTrue(restoredOverlay.isCollapsed)
    }

    @Test
    fun `corrupted persistence file recovers gracefully to clean empty state`() {
        val stateFile = File(tempFolder.root, "corrupted_state.json")
        stateFile.writeText("{not valid json ::: [", Charsets.UTF_8)

        val repo = DefaultPromptBatchRepository(FileBatchPersistence(stateFile))
        assertTrue(repo.batchState.value.isEmpty)
        assertEquals(0, repo.batchState.value.currentIndex)
        assertFalse(repo.overlayState.value.isCollapsed)
    }

    @Test
    fun `phase 2 loadParsedPrompts hook resets batch cleanly`() {
        val repo: PromptBatchRepository = DefaultPromptBatchRepository(InMemoryBatchPersistence())
        val result = repo.loadParsedPrompts(listOf("  Direct API prompt 1  ", "", "Direct API prompt 2"))

        assertEquals(BatchLoadResult.Loaded(2), result)
        assertEquals(listOf("Direct API prompt 1", "Direct API prompt 2"), repo.batchState.value.prompts)
        assertEquals(0, repo.batchState.value.currentIndex)
        assertTrue(repo.batchState.value.copiedIndices.isEmpty())
    }

    @Test
    fun `auto-next advances after insert without wrap-around and persists IME preferences`() {
        val stateFile = File(tempFolder.root, "ime_prefs_state.json")
        val repo = DefaultPromptBatchRepository(FileBatchPersistence(stateFile))
        repo.loadFromRawText("===PROMPT===\nOne\n===PROMPT===\nTwo")

        // Default IME prefs: replaceMode = true, autoNext = false
        assertTrue(repo.imePrefs.value.replaceMode)
        assertFalse(repo.imePrefs.value.autoNext)

        // Manual mode (autoAdvance = false) stays on slot #1 and marks inserted
        assertEquals("One", repo.markCurrentInserted(autoAdvance = false))
        assertEquals(0, repo.batchState.value.currentIndex)
        assertEquals("Inserted ✅ #1/2", repo.batchState.value.insertButtonLabel())

        // Auto-Next ON advances from #1 -> #2, and stops cleanly at #2 (no wrap-around)
        repo.setImeReplaceMode(false)
        repo.setImeAutoNext(true)
        assertEquals("One", repo.markCurrentInserted(autoAdvance = true))
        assertEquals(1, repo.batchState.value.currentIndex)
        assertEquals("Two", repo.markCurrentInserted(autoAdvance = true))
        assertEquals(1, repo.batchState.value.currentIndex) // no wrap-around at #N
        assertEquals(setOf(0, 1), repo.batchState.value.copiedIndices)

        // Restart restores both IME prefs and shared batch state
        val restarted = DefaultPromptBatchRepository(FileBatchPersistence(stateFile))
        assertFalse(restarted.imePrefs.value.replaceMode)
        assertTrue(restarted.imePrefs.value.autoNext)
        assertEquals(1, restarted.batchState.value.currentIndex)
        assertEquals(setOf(0, 1), restarted.batchState.value.copiedIndices)
    }
}
