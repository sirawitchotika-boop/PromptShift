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
    val uncopiedCount: Int get() = (totalCount - copiedCount).coerceAtLeast(0)
    val hasUncopiedPrompts: Boolean get() = uncopiedCount > 0
    val canGoPrevious: Boolean get() = prompts.isNotEmpty() && currentIndex > 0
    val canGoNext: Boolean get() = prompts.isNotEmpty() && currentIndex < prompts.lastIndex
    val displayIndex: Int get() = if (prompts.isEmpty()) 0 else currentIndex + 1

    fun effectiveCurrentPrompt(activeTriggerWord: String? = null): String? {
        val raw = currentPrompt ?: return null
        return PromptTextComposer.applyTriggerWord(raw, activeTriggerWord)
    }

    fun effectiveCharCount(activeTriggerWord: String? = null): Int =
        PromptTextComposer.effectiveLength(currentPrompt, activeTriggerWord)

    fun isCurrentExceedingQuota(activeTriggerWord: String?): Boolean =
        effectiveCharCount(activeTriggerWord) > PromptFormatGuide.SOFT_QUOTA_CHARS

    fun charCountLabel(activeTriggerWord: String? = null): String =
        "${effectiveCharCount(activeTriggerWord)}/${PromptFormatGuide.SOFT_QUOTA_CHARS}"

    fun copyButtonLabel(): String = when {
        isEmpty -> "Tap Load"
        isCurrentCopied -> "Copied ✅ #$displayIndex/$totalCount"
        else -> "Copy #$displayIndex/$totalCount"
    }

    fun insertButtonLabel(): String = when {
        isEmpty -> "Tap Load or AI"
        isCurrentCopied -> "Inserted ✅ #$displayIndex/$totalCount"
        else -> "Insert #$displayIndex/$totalCount"
    }

    fun collapsedBadgeLabel(): String = if (isEmpty) "•" else displayIndex.toString()
}

data class ImePrefsState(
    val replaceMode: Boolean = true,
    val autoNext: Boolean = false,
    val snippetReplaceMode: Boolean = false,
)

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

/**
 * Pure-Kotlin prompt composition helpers for Trigger Words and smart ", " snippet joining.
 */
object PromptTextComposer {

    fun normalizeTriggerWord(raw: String?): String? {
        val cleaned = raw?.trim()?.trimEnd(',', ' ', '\t', '\r', '\n')?.trim()
        return cleaned?.takeIf { it.isNotEmpty() }
    }

    fun applyTriggerWord(body: String, activeTriggerWord: String?): String {
        val cleanBody = body.trim()
        val trigger = normalizeTriggerWord(activeTriggerWord) ?: return cleanBody
        if (cleanBody.isEmpty()) return trigger
        val strippedBody = cleanBody.trimStart(',', ' ', '\t')
        if (strippedBody.isEmpty()) return trigger
        return "$trigger, $strippedBody"
    }

    fun effectiveLength(body: String?, activeTriggerWord: String?): Int {
        if (body.isNullOrBlank()) return 0
        return applyTriggerWord(body, activeTriggerWord).length
    }

    /**
     * Smart ", " joining when appending a snippet at the cursor in the Mini-Keyboard or Direction box:
     * - No leading comma if the existing text is empty or blank.
     * - Avoids double commas ("a, , b") when existing text already ends with ",".
     */
    fun formatSmartJoin(textBeforeCursor: String?, textToInsert: String): String {
        val cleanInsert = textToInsert.trim()
        if (cleanInsert.isEmpty()) return ""
        val before = textBeforeCursor.orEmpty()
        if (before.isBlank()) return cleanInsert
        val trimmedEnd = before.trimEnd()
        return when {
            trimmedEnd.endsWith(",") -> {
                if (before.endsWith(" ") || before.endsWith("\n")) cleanInsert else " $cleanInsert"
            }
            before.endsWith("\n") -> cleanInsert
            else -> ", $cleanInsert"
        }
    }

    fun appendToDraft(existingDraft: String, snippetText: String): String {
        val addition = formatSmartJoin(existingDraft, snippetText)
        if (addition.isEmpty()) return existingDraft
        return if (existingDraft.isBlank()) addition else existingDraft + addition
    }
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

    fun buildStreamingSystemRule(
        promptCount: Int,
        activeTriggerWord: String? = null,
    ): String {
        val safeCount = promptCount.coerceIn(1, 100)
        val normalizedTrigger = PromptTextComposer.normalizeTriggerWord(activeTriggerWord)
        val triggerRule = if (normalizedTrigger != null) {
            val remainingQuota = (SOFT_QUOTA_CHARS - normalizedTrigger.length - 2).coerceAtLeast(100)
            "\n- Trigger Word \"$normalizedTrigger\" is automatically prepended to every prompt by PromptShift — do NOT repeat \"$normalizedTrigger\" inside the generated prompt body (keep each generated body under $remainingQuota characters)."
        } else {
            ""
        }
        return """
            [OUTPUT FORMAT — STRICT]
            - Generate exactly $safeCount prompts.
            - Put ===PROMPT=== on its own line immediately before each prompt.
            - Output ONLY the final prompt text in each slot — no numbering, no titles, no labels (such as "Prompt 1:"), no negative prompt section, no markdown prose, and no extra commentary.
            - Keep each individual prompt under $SOFT_QUOTA_CHARS characters.$triggerRule
        """.trimIndent()
    }
}
