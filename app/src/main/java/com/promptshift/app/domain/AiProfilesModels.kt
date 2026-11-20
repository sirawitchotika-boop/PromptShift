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
    val favoriteModels: List<String> = emptyList(),
    val hasApiKey: Boolean = false,
) {
    val allSelectableModels: List<String>
        get() = (listOf(modelId) + favoriteModels)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

data class InstructionProfile(
    val id: String,
    val name: String,
    val body: String,
)

data class PromptSnippet(
    val id: String,
    val title: String,
    val body: String,
    val includeTriggerWord: Boolean = false,
)

enum class ReasoningEffort(val apiValue: String?, val label: String) {
    DEFAULT(null, "Default (Off)"),
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High");

    companion object {
        fun fromKey(raw: String?): ReasoningEffort =
            entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.apiValue.equals(raw, ignoreCase = true)
            } ?: DEFAULT
    }
}

data class AiConfigState(
    val providers: List<ProviderProfile> = emptyList(),
    val activeProviderId: String? = null,
    val instructions: List<InstructionProfile> = emptyList(),
    val activeInstructionId: String? = null,
    val defaultPromptCount: Int = 20,
    val reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
    val triggerWords: List<String> = emptyList(),
    val activeTriggerWord: String? = null,
    val triggerWordEnabled: Boolean = false,
    val snippets: List<PromptSnippet> = emptyList(),
    val recentPrompts: List<String> = emptyList(),
) {
    val activeProvider: ProviderProfile?
        get() = providers.firstOrNull { it.id == activeProviderId } ?: providers.firstOrNull()

    val activeInstruction: InstructionProfile?
        get() = activeInstructionId?.let { id -> instructions.firstOrNull { it.id == id } }

    val effectiveTriggerWord: String?
        get() = if (triggerWordEnabled) PromptTextComposer.normalizeTriggerWord(activeTriggerWord) else null
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

sealed interface FetchModelsResult {
    data class Success(val models: List<String>) : FetchModelsResult
    data class Failure(val message: String) : FetchModelsResult
}

sealed interface InstructionImportResult {
    data class Imported(val count: Int) : InstructionImportResult
    data class Failed(val reason: String) : InstructionImportResult
}

sealed interface SafeBackupImportResult {
    data class Imported(
        val instructionsCount: Int,
        val snippetsCount: Int,
        val triggerWordsCount: Int,
        val favoriteModelsCount: Int,
    ) : SafeBackupImportResult {
        fun summaryLabel(): String {
            val parts = buildList {
                if (instructionsCount > 0) add("$instructionsCount preset${if (instructionsCount == 1) "" else "s"}")
                if (snippetsCount > 0) add("$snippetsCount snippet${if (snippetsCount == 1) "" else "s"}")
                if (triggerWordsCount > 0) add("$triggerWordsCount trigger${if (triggerWordsCount == 1) "" else "s"}")
                if (favoriteModelsCount > 0) add("$favoriteModelsCount fav model${if (favoriteModelsCount == 1) "" else "s"}")
            }
            return if (parts.isEmpty()) "Backup imported" else "Imported " + parts.joinToString(", ")
        }
    }

    data class Failed(val reason: String) : SafeBackupImportResult
}
