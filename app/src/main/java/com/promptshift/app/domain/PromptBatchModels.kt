package com.promptshift.app.domain

data class PromptBatchState(
    val prompts: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val copiedIndices: Set<Int> = emptySet(),
) {
    val totalCount: Int get() = prompts.size
    val isEmpty: Boolean get() = prompts.isEmpty()
    val currentPrompt: String? get() = prompts.getOrNull(currentIndex)
    val currentCharCount: Int get() = currentPrompt?.length ?: 0
    val isCurrentExceedingQuota: Boolean get() = currentCharCount > PromptFormatGuide.SOFT_QUOTA_CHARS
    val isCurrentCopied: Boolean get() = prompts.isNotEmpty() && currentIndex in copiedIndices
    val copiedCount: Int get() = copiedIndices.count { it in prompts.indices }
    val canGoPrevious: Boolean get() = prompts.isNotEmpty() && currentIndex > 0
    val canGoNext: Boolean get() = prompts.isNotEmpty() && currentIndex < prompts.lastIndex
    val displayIndex: Int get() = if (prompts.isEmpty()) 0 else currentIndex + 1

    fun charCountLabel(): String = "$currentCharCount/${PromptFormatGuide.SOFT_QUOTA_CHARS}"

    fun copyButtonLabel(): String = when {
        isEmpty -> "Tap Load"
        isCurrentCopied -> "Copied ✅ #$displayIndex/$totalCount"
        else -> "Copy #$displayIndex/$totalCount"
    }

    fun collapsedBadgeLabel(): String = if (isEmpty) "•" else displayIndex.toString()
}

data class OverlayUiState(
    val x: Int = 0,
    val y: Int = 180,
    val isCollapsed: Boolean = false,
    val hasSavedPosition: Boolean = false,
)

data class OverlayFeedback(
    val id: Long,
    val message: String,
    val isError: Boolean = false,
)

sealed interface BatchParseResult {
    data class Success(val prompts: List<String>) : BatchParseResult
    data object NoDelimiterFound : BatchParseResult
    data object EmptyAfterCleaning : BatchParseResult
}

sealed interface BatchLoadResult {
    data class Loaded(val count: Int) : BatchLoadResult
    data object PreservedNoDelimiter : BatchLoadResult
    data object PreservedEmpty : BatchLoadResult
}

object PromptFormatGuide {
    const val DELIMITER = "===PROMPT==="
    const val SOFT_QUOTA_CHARS = 2000

    val SAMPLE_BLOCK: String = """
        ===PROMPT===
        <prompt #1 — up to 2,000 chars>
        ===PROMPT===
        <prompt #2 — up to 2,000 chars>
    """.trimIndent()

    val AI_FORMAT_INSTRUCTION: String = """
        [OUTPUT FORMAT — STRICT]
        - Return ALL generated prompts inside ONE single plain-text code block.
        - Put ===PROMPT=== on its own line immediately before each prompt.
        - Output ONLY the final prompt text in each slot — no numbering, no titles, no labels (such as "Prompt 1:"), no negative prompt section, and no extra commentary inside the code block.
        - Keep each individual prompt under 2,000 characters.

        Required layout:
        ===PROMPT===
        <prompt text>
        ===PROMPT===
        <prompt text>
        ===PROMPT===
        <prompt text>
    """.trimIndent()
}
