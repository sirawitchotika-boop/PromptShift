package com.promptshift.app.domain

object PromptBatchParser {
    private val DELIMITER_LINE_REGEX = Regex(
        pattern = "^[ \\t]*===PROMPT===[ \\t]*$",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

    private val OPENING_FENCE_LINE_REGEX = Regex("^[ \\t]*```[^\\n]*$")
    private val CLOSING_FENCE_LINE_REGEX = Regex("^[ \\t]*```[ \\t]*$")

    private val FENCED_BLOCK_REGEX = Regex(
        pattern = "^[ \\t]*```[^\\n]*\\n([\\s\\S]*?)\\n[ \\t]*```[ \\t]*$",
        options = setOf(RegexOption.MULTILINE),
    )

    fun parse(rawText: String?): BatchParseResult {
        if (rawText.isNullOrBlank()) {
            return BatchParseResult.NoDelimiterFound
        }

        val normalized = rawText
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val unwrapped = stripMarkdownCodeFences(normalized)

        if (!DELIMITER_LINE_REGEX.containsMatchIn(unwrapped)) {
            return BatchParseResult.NoDelimiterFound
        }

        val prompts = unwrapped
            .split(DELIMITER_LINE_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (prompts.isEmpty()) {
            return BatchParseResult.EmptyAfterCleaning
        }

        return BatchParseResult.Success(prompts)
    }

    internal fun stripMarkdownCodeFences(normalizedText: String): String {
        val trimmedLines = normalizedText.trim().lines()
        if (trimmedLines.isEmpty()) return ""

        // Case 1: The entire payload is wrapped in outer triple-backtick fences (e.g. ```text ... ```).
        if (OPENING_FENCE_LINE_REGEX.matches(trimmedLines.first())) {
            val startIdx = 1
            val endIdx = if (trimmedLines.size > 1 && CLOSING_FENCE_LINE_REGEX.matches(trimmedLines.last())) {
                trimmedLines.size - 1
            } else {
                trimmedLines.size
            }
            if (startIdx <= endIdx) {
                return trimmedLines.subList(startIdx, endIdx).joinToString("\n").trim()
            }
        }

        // Case 2: A fenced code block containing ===PROMPT=== appears inside copied chat prose.
        val fencedMatch = FENCED_BLOCK_REGEX.findAll(normalizedText)
            .firstOrNull { match -> DELIMITER_LINE_REGEX.containsMatchIn(match.groupValues[1]) }
        if (fencedMatch != null) {
            return fencedMatch.groupValues[1].trim()
        }

        return normalizedText
    }
}
