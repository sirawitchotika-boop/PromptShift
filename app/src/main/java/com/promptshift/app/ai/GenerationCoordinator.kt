package com.promptshift.app.ai

import com.promptshift.app.data.AiConfigRepository
import com.promptshift.app.data.PromptBatchRepository
import com.promptshift.app.domain.ConnectionTestResult
import com.promptshift.app.domain.GenerationState
import com.promptshift.app.domain.StartGenerationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Coordinates direct OpenAI-compatible SSE streaming generation across both the Main Hub
 * and the Floating Bubble's Quick-Direction popup.
 */
class GenerationCoordinator(
    private val appScope: CoroutineScope,
    private val batchRepository: PromptBatchRepository,
    private val configRepository: AiConfigRepository,
    private val streamingClient: AiStreamingClient,
) {
    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private var activeJob: Job? = null

    fun startGeneration(
        direction: String,
        promptCount: Int,
        allowReplaceExisting: Boolean = false,
    ): StartGenerationResult {
        if (activeJob?.isActive == true) {
            return StartGenerationResult.Blocked("Generation is already streaming")
        }

        val config = configRepository.configState.value
        val provider = config.activeProvider
            ?: return StartGenerationResult.Blocked("Add an AI provider profile first")
        val apiKey = configRepository.getApiKey(provider.id)?.takeIf { it.isNotBlank() }
            ?: return StartGenerationResult.Blocked("Missing API key for ${provider.name}")

        val instruction = config.activeInstruction
        if (direction.isBlank() && instruction == null) {
            return StartGenerationResult.Blocked("Enter a direction or select an Instruction Profile")
        }

        val currentBatch = batchRepository.batchState.value
        if (!allowReplaceExisting && currentBatch.hasUncopiedPrompts) {
            return StartGenerationResult.NeedsOverwriteConfirmation(currentBatch.uncopiedCount)
        }

        val safeCount = promptCount.coerceIn(1, 100)
        configRepository.setDefaultPromptCount(safeCount)

        batchRepository.beginStreamingBatch()
        _generationState.value = GenerationState.Streaming(
            providerName = provider.name,
            targetCount = safeCount,
            unlockedCount = 0,
        )
        batchRepository.postFeedback("Streaming 0/$safeCount...")

        activeJob = appScope.launch {
            var unlocked = 0
            try {
                val total = streamingClient.streamPrompts(
                    provider = provider,
                    apiKey = apiKey,
                    instruction = instruction,
                    direction = direction,
                    promptCount = safeCount,
                    onPromptCompleted = { prompt ->
                        unlocked = batchRepository.appendStreamedPrompt(prompt)
                        _generationState.value = GenerationState.Streaming(
                            providerName = provider.name,
                            targetCount = safeCount,
                            unlockedCount = unlocked,
                        )
                    },
                )

                if (total > 0) {
                    _generationState.value = GenerationState.Completed(totalCount = total, cancelled = false)
                    batchRepository.postFeedback("Ready: $total prompt${if (total == 1) "" else "s"}")
                } else {
                    val msg = "No ===PROMPT=== slots returned by ${provider.name}"
                    _generationState.value = GenerationState.Error(msg)
                    batchRepository.postFeedback("No ===PROMPT=== found", isError = true)
                }
            } catch (_: CancellationException) {
                val saved = batchRepository.batchState.value.totalCount
                _generationState.value = GenerationState.Completed(totalCount = saved, cancelled = true)
                batchRepository.postFeedback(
                    if (saved > 0) "Stopped ($saved ready)" else "Stream cancelled",
                    isError = saved == 0,
                )
            } catch (e: Exception) {
                val msg = e.message?.take(140) ?: "Streaming failed"
                _generationState.value = GenerationState.Error(msg)
                batchRepository.postFeedback(msg.take(28), isError = true)
            } finally {
                activeJob = null
            }
        }

        return StartGenerationResult.Started
    }

    fun cancelGeneration() {
        activeJob?.cancel()
    }

    suspend fun testConnection(providerId: String): ConnectionTestResult {
        val provider = configRepository.configState.value.providers.firstOrNull { it.id == providerId }
            ?: return ConnectionTestResult.Failure("Provider profile not found")
        val apiKey = configRepository.getApiKey(providerId)?.takeIf { it.isNotBlank() }
            ?: return ConnectionTestResult.Failure("Save an API key for ${provider.name} first")
        return streamingClient.testConnection(provider, apiKey)
    }
}
