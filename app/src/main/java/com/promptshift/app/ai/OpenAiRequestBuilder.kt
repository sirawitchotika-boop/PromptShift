package com.promptshift.app.ai

import com.promptshift.app.data.AiConfigCodec
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.domain.ProviderProfile

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

    fun buildSystemMessage(
        instruction: InstructionProfile?,
        promptCount: Int,
    ): String {
        val formatRule = PromptFormatGuide.buildStreamingSystemRule(promptCount)
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
    ): String {
        val systemContent = buildSystemMessage(instruction, promptCount)
        val userContent = buildUserMessage(direction, promptCount)

        val sb = StringBuilder(systemContent.length + userContent.length + 192)
        sb.append("{\"model\":")
        AiConfigCodec.appendJsonString(sb, provider.modelId.trim())
        sb.append(",\"stream\":true,\"messages\":[")
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
