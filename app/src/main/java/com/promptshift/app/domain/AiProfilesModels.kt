package com.promptshift.app.domain

/**
 * Provider-agnostic OpenAI-compatible AI endpoint metadata.
 * API keys are NEVER held inside this metadata object — they are stored separately
 * in Android Keystore-backed SecureApiKeyStore keyed by [id].
 */
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val modelId: String,
    val hasApiKey: Boolean = false,
)

data class InstructionProfile(
    val id: String,
    val name: String,
    val body: String,
)

data class AiConfigState(
    val providers: List<ProviderProfile> = emptyList(),
    val activeProviderId: String? = null,
    val instructions: List<InstructionProfile> = emptyList(),
    val activeInstructionId: String? = null,
    val defaultPromptCount: Int = 20,
) {
    val activeProvider: ProviderProfile?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()

    val activeInstruction: InstructionProfile?
        get() = activeInstructionId?.let { id -> instructions.firstOrNull { it.id == id } }
}

sealed interface GenerationState {
    data object Idle : GenerationState

    data class Streaming(
        val providerName: String,
        val targetCount: Int,
        val unlockedCount: Int,
    ) : GenerationState

    data class Completed(
        val totalCount: Int,
        val cancelled: Boolean = false,
    ) : GenerationState

    data class Error(val message: String) : GenerationState

    val isStreaming: Boolean get() = this is Streaming
}

sealed interface StartGenerationResult {
    data object Started : StartGenerationResult
    data class NeedsOverwriteConfirmation(val uncopiedCount: Int) : StartGenerationResult
    data class Blocked(val reason: String) : StartGenerationResult
}

sealed interface ConnectionTestResult {
    data class Success(val latencyMs: Long, val detail: String) : ConnectionTestResult
    data class Failure(val message: String) : ConnectionTestResult
}

sealed interface InstructionImportResult {
    data class Imported(val count: Int) : InstructionImportResult
    data class Failed(val reason: String) : InstructionImportResult
}
