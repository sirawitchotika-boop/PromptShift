package com.promptshift.app.ai

import com.promptshift.app.data.AiConfigCodec
import com.promptshift.app.data.SimpleJsonParser
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.domain.ReasoningEffort

object OpenAiRequestBuilder {

    /**
     * Resolves an OpenAI-compatible base URL into its `/chat/completions` endpoint.
     * Supports inputs like:
     * - https://api.openai.com/v1
     * - https://openrouter.ai/api/v1/
     * - https://example.com/v1/chat/completions
     */
    fun resolveChatCompletionsUrl(rawBaseUrl: String): String {
        val trimmed = rawBaseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.endsWith("/chat/completions", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/chat/completions"
        }
    }

    /**
     * Resolves an OpenAI-compatible base URL into its `/models` endpoint.
     */
    fun resolveModelsUrl(rawBaseUrl: String): String {
        var trimmed = rawBaseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val chatSuffix = "/chat/completions"
        if (trimmed.endsWith(chatSuffix, ignoreCase = true)) {
            trimmed = trimmed.dropLast(chatSuffix.length).trimEnd('/')
        }
        return if (trimmed.endsWith("/models", ignoreCase = true)) {
            trimmed
        } else {
            "$trimmed/models"
        }
    }

    fun parseModelsResponse(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val parsed = SimpleJsonParser(json).parseValue() ?: return emptyList()
            val rawList = when (parsed) {
                is Map<*, *> -> (parsed["data"] as? List<*>) ?: (parsed["models"] as? List<*>)
                is List<*> -> parsed
                else -> null
            } ?: return emptyList()

            rawList.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> ((item["id"] as? String) ?: (item["name"] as? String))?.trim()
                    is String -> item.trim()
                    else -> null
                }?.takeIf { it.isNotEmpty() }
            }.distinct().sorted()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildSystemMessage(
        instruction: InstructionProfile?,
        promptCount: Int,
        activeTriggerWord: String? = null,
    ): String {
        val formatRule = PromptFormatGuide.buildStreamingSystemRule(
            promptCount = promptCount,
            activeTriggerWord = activeTriggerWord,
        )
        val userInstruction = instruction?.body?.trim().orEmpty()
        return if (userInstruction.isEmpty()) {
            formatRule
        } else {
            "$userInstruction\n\n$formatRule"
        }
    }

    fun buildUserMessage(
        direction: String,
        promptCount: Int,
    ): String {
        val safeCount = promptCount.coerceIn(1, 100)
        val cleaned = direction.trim()
        return if (cleaned.isEmpty()) {
            "Generate $safeCount prompts following the system instructions."
        } else {
            "$cleaned\n\nGenerate exactly $safeCount prompts."
        }
    }

    fun buildStreamingRequestJson(
        provider: ProviderProfile,
        instruction: InstructionProfile?,
        direction: String,
        promptCount: Int,
        reasoningEffort: ReasoningEffort = ReasoningEffort.DEFAULT,
        activeTriggerWord: String? = null,
    ): String {
        val systemContent = buildSystemMessage(instruction, promptCount, activeTriggerWord)
        val userContent = buildUserMessage(direction, promptCount)

        val sb = StringBuilder(systemContent.length + userContent.length + 224)
        sb.append("{\"model\":")
        AiConfigCodec.appendJsonString(sb, provider.modelId.trim())
        sb.append(",\"stream\":true")
        val effortValue = reasoningEffort.apiValue
        if (effortValue != null) {
            sb.append(",\"reasoning_effort\":")
            AiConfigCodec.appendJsonString(sb, effortValue)
        }
        sb.append(",\"messages\":[")
        sb.append("{\"role\":\"system\",\"content\":")
        AiConfigCodec.appendJsonString(sb, systemContent)
        sb.append("},{\"role\":\"user\",\"content\":")
        AiConfigCodec.appendJsonString(sb, userContent)
        sb.append("}]}")
        return sb.toString()
    }

    fun buildConnectionTestJson(provider: ProviderProfile): String {
        val sb = StringBuilder(128)
        sb.append("{\"model\":")
        AiConfigCodec.appendJsonString(sb, provider.modelId.trim())
        sb.append(",\"stream\":false,\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":\"ok\"}]}")
        return sb.toString()
    }
}
