package com.promptshift.app.ai

import com.promptshift.app.data.AiConfigPersistence
import com.promptshift.app.data.ApiKeyStore
import com.promptshift.app.data.BatchPersistence
import com.promptshift.app.data.DefaultAiConfigRepository
import com.promptshift.app.data.DefaultPromptBatchRepository
import com.promptshift.app.data.PersistedAiConfig
import com.promptshift.app.data.PersistedBatchSnapshot
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.domain.ReasoningEffort
import com.promptshift.app.domain.StartGenerationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationCoordinatorTest {

    private class MemoryBatchPersistence : BatchPersistence {
        var snapshot: PersistedBatchSnapshot? = null
        override fun load(): PersistedBatchSnapshot? = snapshot
        override fun save(snapshot: PersistedBatchSnapshot) { this.snapshot = snapshot }
    }

    private class MemoryConfigPersistence : AiConfigPersistence {
        var config: PersistedAiConfig? = null
        override fun load(): PersistedAiConfig? = config
        override fun save(config: PersistedAiConfig) { this.config = config }
    }

    private class MemoryKeyStore : ApiKeyStore {
        private val keys = mutableMapOf<String, String>()
        override fun saveKey(profileId: String, apiKey: String) { keys[profileId] = apiKey }
        override fun getKey(profileId: String): String? = keys[profileId]
        override fun deleteKey(profileId: String) { keys.remove(profileId) }
        override fun hasKey(profileId: String): Boolean = keys.containsKey(profileId)
    }

    @Test
    fun `warns before replacing uncopied prompts and allows immediate stream once confirmed or all copied`() = runTest {
        val batchRepo = DefaultPromptBatchRepository(MemoryBatchPersistence())
        val configRepo = DefaultAiConfigRepository(MemoryConfigPersistence(), MemoryKeyStore())
        configRepo.saveProvider(null, "Primary", "https://api.example.com/v1", "model-1", "sk-secret-123")
        configRepo.setReasoningEffort(ReasoningEffort.MEDIUM)
        configRepo.addTriggerWordChip("ohwx woman")

        var capturedEffort: ReasoningEffort? = null
        var capturedTrigger: String? = null

        val fakeClient = object : AiStreamingClient {
            override suspend fun streamPrompts(
                provider: ProviderProfile,
                apiKey: String,
                instruction: InstructionProfile?,
                direction: String,
                promptCount: Int,
                reasoningEffort: ReasoningEffort,
                activeTriggerWord: String?,
                onPromptCompleted: (String) -> Unit,
            ): Int {
                capturedEffort = reasoningEffort
                capturedTrigger = activeTriggerWord
                onPromptCompleted("Streamed slot 1")
                onPromptCompleted("Streamed slot 2")
                return 2
            }

            override suspend fun testConnection(provider: ProviderProfile, apiKey: String) =
                ConnectionTestResult.Success(42, "ok")
        }

        val coordinator = GenerationCoordinator(this, batchRepo, configRepo, fakeClient)

        // Existing batch with 2 uncopied prompts
        batchRepo.loadFromRawText("===PROMPT===\nOld 1\n===PROMPT===\nOld 2")

        // Blocked by overwrite guard until confirmed
        val guarded = coordinator.startGeneration("new theme", 2, allowReplaceExisting = false)
        assertEquals(StartGenerationResult.NeedsOverwriteConfirmation(2), guarded)
        assertEquals(listOf("Old 1", "Old 2"), batchRepo.batchState.value.prompts)

        // Confirmed -> replaces and streams live with reasoningEffort & activeTriggerWord
        val started = coordinator.startGeneration("new theme", 2, allowReplaceExisting = true)
        assertEquals(StartGenerationResult.Started, started)
        advanceUntilIdle()

        assertEquals(ReasoningEffort.MEDIUM, capturedEffort)
        assertEquals("ohwx woman", capturedTrigger)
        assertEquals(listOf("Streamed slot 1", "Streamed slot 2"), batchRepo.batchState.value.prompts)
        assertEquals(GenerationState.Completed(2, cancelled = false), coordinator.generationState.value)

        // Mark all prompts copied -> next generation starts WITHOUT needing confirmation
        batchRepo.markCurrentCopied()
        batchRepo.stepNext()
        batchRepo.markCurrentCopied()
        assertEquals(StartGenerationResult.Started, coordinator.startGeneration("another theme", 2, allowReplaceExisting = false))
        advanceUntilIdle()
        assertEquals(2, batchRepo.batchState.value.totalCount)
    }

    @Test
    fun `cancel mid-stream preserves already unlocked prompts and keeps copied checkmarks`() = runTest {
        val batchRepo = DefaultPromptBatchRepository(MemoryBatchPersistence())
        val configRepo = DefaultAiConfigRepository(MemoryConfigPersistence(), MemoryKeyStore())
        configRepo.saveProvider(null, "Primary", "https://api.example.com/v1", "model-1", "sk-secret-123")

        val gate = CompletableDeferred<Unit>()
        val fakeClient = object : AiStreamingClient {
            override suspend fun streamPrompts(
                provider: ProviderProfile,
                apiKey: String,
                instruction: InstructionProfile?,
                direction: String,
                promptCount: Int,
                reasoningEffort: ReasoningEffort,
                activeTriggerWord: String?,
                onPromptCompleted: (String) -> Unit,
            ): Int {
                onPromptCompleted("Unlocked #1")
                onPromptCompleted("Unlocked #2")
                gate.await() // hangs until cancelled
                onPromptCompleted("Never reached")
                return 3
            }

            override suspend fun testConnection(provider: ProviderProfile, apiKey: String) =
                ConnectionTestResult.Success(20, "ok")
        }

        val coordinator = GenerationCoordinator(this, batchRepo, configRepo, fakeClient)
        assertEquals(StartGenerationResult.Started, coordinator.startGeneration("cyberpunk", 10))
        testScheduler.runCurrent()

        // While still streaming, slots #1 and #2 are already unlocked and copyable!
        assertEquals(listOf("Unlocked #1", "Unlocked #2"), batchRepo.batchState.value.prompts)
        assertEquals("Unlocked #1", batchRepo.markCurrentCopied())
        assertTrue(coordinator.generationState.value.isStreaming)

        // Cancel stream -> keeps unlocked slots & checkmark safely
        coordinator.cancelGeneration()
        advanceUntilIdle()

        assertEquals(GenerationState.Completed(totalCount = 2, cancelled = true), coordinator.generationState.value)
        assertEquals(listOf("Unlocked #1", "Unlocked #2"), batchRepo.batchState.value.prompts)
        assertTrue(batchRepo.batchState.value.isCurrentCopied)
    }
}
