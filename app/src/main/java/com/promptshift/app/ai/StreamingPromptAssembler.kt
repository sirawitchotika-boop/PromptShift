package com.promptshift.app.ai

import com.promptshift.app.data.SimpleJsonParser

sealed interface SseEvent {
    data class ContentDelta(val text: String) : SseEvent
    data object Done : SseEvent
    data class Error(val message: String) : SseEvent
    data object Ignore : SseEvent
}

/**
 * Parses OpenAI-compatible SSE `data:` lines and assembles completed `===PROMPT===` slots
 * incrementally in real time.
 *
 * Guarantees:
 * - Strips `<think>...</think>` tags across arbitrary chunk boundaries (case-insensitive).
 * - Ignores `delta.reasoning_content` / `delta.reasoning` fields from reasoning models.
 * - Strips standalone wrapping markdown triple-backtick code fences (` ``` `, ` ```text `).
 * - Handles `===PROMPT===` delimiters split across chunks.
 * - Emits each prompt slot ONLY once it is complete (on the next delimiter or on `finish()`).
 */
class StreamingPromptAssembler(
    private val onPromptCompleted: (String) -> Unit,
) {
    private val thinkFilter = ThinkTagFilter()
    private val lineBuffer = StringBuilder()
    private val currentSlot = StringBuilder()
    private var insidePromptSlot = false
    private var emittedCount = 0

    val completedCount: Int get() = emittedCount

    fun feedTextDelta(delta: String) {
        if (delta.isEmpty()) return
        val normalized = delta.replace("\r\n", "\n").replace("\r", "\n")
        val filtered = thinkFilter.feed(normalized)
        if (filtered.isEmpty()) return
        processFilteredText(filtered)
    }

    /**
     * Call when the stream finishes normally (`data: [DONE]` or EOF).
     * Flushes the final buffered prompt if non-empty.
     */
    fun finish() {
        val trailing = thinkFilter.flush()
        if (trailing.isNotEmpty()) {
            processFilteredText(trailing)
        }
        if (lineBuffer.isNotEmpty()) {
            processCompleteLine(lineBuffer.toString())
            lineBuffer.setLength(0)
        }
        flushCurrentSlotIfReady()
    }

    private fun processFilteredText(text: String) {
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '\n') {
                processCompleteLine(lineBuffer.toString())
                lineBuffer.setLength(0)
            } else {
                lineBuffer.append(ch)
            }
        }
    }

    private fun processCompleteLine(line: String) {
        if (FENCE_LINE_REGEX.matches(line)) {
            return
        }

        if (DELIMITER_LINE_REGEX.matches(line)) {
            if (insidePromptSlot) {
                flushCurrentSlotIfReady()
            }
            insidePromptSlot = true
            return
        }

        if (insidePromptSlot) {
            if (currentSlot.isNotEmpty()) {
                currentSlot.append('\n')
            }
            currentSlot.append(line)
        }
    }

    private fun flushCurrentSlotIfReady() {
        val cleaned = currentSlot.toString().trim()
        currentSlot.setLength(0)
        if (cleaned.isNotEmpty()) {
            emittedCount++
            onPromptCompleted(cleaned)
        }
    }

    companion object {
        private val DELIMITER_LINE_REGEX = Regex(
            pattern = "^[ \\t]*===PROMPT===[ \\t]*$",
            options = setOf(RegexOption.IGNORE_CASE),
        )
        private val FENCE_LINE_REGEX = Regex("^[ \\t]*```[^\\n]*$")

        /**
         * Parses a single SSE line (e.g. `data: {...}` or `data: [DONE]`).
         * Also parses raw JSON error objects returned by providers.
         */
        fun parseSseLine(rawLine: String): SseEvent {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith(":")) {
                return SseEvent.Ignore
            }

            val payload = when {
                trimmed.startsWith("data:", ignoreCase = true) -> trimmed.substring(5).trim()
                trimmed.startsWith("{") -> trimmed
                else -> return SseEvent.Ignore
            }

            if (payload.isEmpty()) return SseEvent.Ignore
            if (payload == "[DONE]") return SseEvent.Done

            val parsed = SimpleJsonParser(payload).parseValue() as? Map<*, *> ?: return SseEvent.Ignore

            // Check for top-level error object
            val errorObj = parsed["error"]
            if (errorObj is Map<*, *>) {
                val msg = (errorObj["message"] as? String)?.takeIf { it.isNotBlank() }
                    ?: "Provider returned an API error"
                return SseEvent.Error(msg)
            } else if (errorObj is String && errorObj.isNotBlank()) {
                return SseEvent.Error(errorObj)
            }

            val choices = parsed["choices"] as? List<*> ?: return SseEvent.Ignore
            val firstChoice = choices.firstOrNull() as? Map<*, *> ?: return SseEvent.Ignore

            // Standard streaming delta.content (strictly ignores reasoning_content / reasoning)
            val delta = firstChoice["delta"] as? Map<*, *>
            val deltaContent = extractTextContent(delta?.get("content"))
            if (!deltaContent.isNullOrEmpty()) {
                return SseEvent.ContentDelta(deltaContent)
            }

            // Fallback for providers returning non-streaming message.content
            val message = firstChoice["message"] as? Map<*, *>
            val messageContent = extractTextContent(message?.get("content"))
            if (!messageContent.isNullOrEmpty()) {
                return SseEvent.ContentDelta(messageContent)
            }

            return SseEvent.Ignore
        }

        private fun extractTextContent(value: Any?): String? = when (value) {
            is String -> value
            is List<*> -> buildString {
                value.forEach { part ->
                    val map = part as? Map<*, *>
                    val text = map?.get("text") as? String
                    if (text != null) append(text)
                }
            }.takeIf { it.isNotEmpty() }
            else -> null
        }
    }
}

/**
 * Deterministic O(N) filter that removes `<think>...</think>` blocks across arbitrary chunk splits.
 */
internal class ThinkTagFilter {
    private var inThink = false
    private val pending = StringBuilder()

    fun feed(chunk: String): String {
        if (chunk.isEmpty()) return ""
        val combined = if (pending.isEmpty()) {
            chunk
        } else {
            val s = pending.toString() + chunk
            pending.setLength(0)
            s
        }

        val out = StringBuilder(combined.length)
        var i = 0
        while (i < combined.length) {
            if (!inThink) {
                val openIdx = indexOfIgnoreCase(combined, OPEN_TAG, i)
                if (openIdx >= 0) {
                    out.append(combined, i, openIdx)
                    inThink = true
                    i = openIdx + OPEN_TAG.length
                } else {
                    val partialLen = trailingPrefixLength(combined, i, OPEN_TAG)
                    val safeEnd = combined.length - partialLen
                    if (safeEnd > i) {
                        out.append(combined, i, safeEnd)
                    }
                    if (partialLen > 0) {
                        pending.append(combined, safeEnd, combined.length)
                    }
                    break
                }
            } else {
                val closeIdx = indexOfIgnoreCase(combined, CLOSE_TAG, i)
                if (closeIdx >= 0) {
                    inThink = false
                    i = closeIdx + CLOSE_TAG.length
                } else {
                    val partialLen = trailingPrefixLength(combined, i, CLOSE_TAG)
                    if (partialLen > 0) {
                        pending.append(combined, combined.length - partialLen, combined.length)
                    }
                    break
                }
            }
        }
        return out.toString()
    }

    fun flush(): String {
        val result = if (!inThink && pending.isNotEmpty()) pending.toString() else ""
        pending.setLength(0)
        return result
    }

    private fun indexOfIgnoreCase(haystack: String, needle: String, startIndex: Int): Int {
        val maxStart = haystack.length - needle.length
        for (i in startIndex..maxStart) {
            if (haystack.regionMatches(i, needle, 0, needle.length, ignoreCase = true)) {
                return i
            }
        }
        return -1
    }

    private fun trailingPrefixLength(text: String, startIndex: Int, tag: String): Int {
        val available = text.length - startIndex
        val maxLen = minOf(available, tag.length - 1)
        for (len in maxLen downTo 1) {
            if (text.regionMatches(text.length - len, tag, 0, len, ignoreCase = true)) {
                return len
            }
        }
        return 0
    }

    companion object {
        private const val OPEN_TAG = "<think>"
        private const val CLOSE_TAG = "</think>"
    }
}
