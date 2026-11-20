package com.promptshift.app.ai

import com.promptshift.app.data.SimpleJsonParser
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.domain.ProviderProfile
import com.promptshift.app.domain.ReasoningEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiRequestBuilderTest {

    @Test
    fun `resolves chat completions endpoint cleanly for common base URL formats`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiRequestBuilder.resolveChatCompletionsUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://openrouter.ai/api/v1/chat/completions",
            OpenAiRequestBuilder.resolveChatCompletionsUrl("https://openrouter.ai/api/v1/"),
        )
        assertEquals(
            "https://custom.example.com/v1/chat/completions",
            OpenAiRequestBuilder.resolveChatCompletionsUrl("https://custom.example.com/v1/chat/completions"),
        )
    }

    @Test
    fun `resolves models URL and parses OpenAI models list response`() {
        assertEquals(
            "https://openrouter.ai/api/v1/models",
            OpenAiRequestBuilder.resolveModelsUrl("https://openrouter.ai/api/v1/"),
        )
        assertEquals(
            "https://api.openai.com/v1/models",
            OpenAiRequestBuilder.resolveModelsUrl("https://api.openai.com/v1/chat/completions"),
        )

        val response = """
            {"object":"list","data":[{"id":"gpt-4o-mini"},{"id":"deepseek/deepseek-chat"},{"id":"gpt-4o-mini"}]}
        """.trimIndent()
        assertEquals(
            listOf("deepseek/deepseek-chat", "gpt-4o-mini"),
            OpenAiRequestBuilder.parseModelsResponse(response),
        )
    }

    @Test
    fun `auto-appends strict delimiter format rule with count and optional instruction`() {
        val provider = ProviderProfile(
            id = "p1",
            name = "Primary",
            baseUrl = "https://api.example.com/v1",
            modelId = "model-x",
        )
        val instruction = InstructionProfile(
            id = "i1",
            name = "Cinematic",
            body = "Use moody 35mm film color grading.",
        )

        val jsonWithInstruction = OpenAiRequestBuilder.buildStreamingRequestJson(
            provider = provider,
            instruction = instruction,
            direction = "rainy neon night market",
            promptCount = 15,
        )
        val parsed = SimpleJsonParser(jsonWithInstruction).parseValue() as Map<*, *>
        assertEquals("model-x", parsed["model"])
        assertEquals(true, parsed["stream"])
        assertFalse(parsed.containsKey("reasoning_effort"))

        val messages = parsed["messages"] as List<*>
        assertEquals(2, messages.size)
        val systemContent = (messages[0] as Map<*, *>)["content"] as String
        val userContent = (messages[1] as Map<*, *>)["content"] as String

        assertTrue(systemContent.contains("Use moody 35mm film color grading."))
        assertTrue(systemContent.contains(PromptFormatGuide.DELIMITER))
        assertTrue(systemContent.contains("Generate exactly 15 prompts"))
        assertTrue(userContent.contains("rainy neon night market"))

        // With None instruction, system message still enforces ===PROMPT=== rule
        val systemNoInstruction = OpenAiRequestBuilder.buildSystemMessage(null, 5)
        assertTrue(systemNoInstruction.contains(PromptFormatGuide.DELIMITER))
        assertTrue(systemNoInstruction.contains("Generate exactly 5 prompts"))
    }

    @Test
    fun `includes reasoning_effort when enabled and instructs AI not to repeat active trigger word`() {
        val provider = ProviderProfile(
            id = "p1",
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "openai/o3-mini",
        )
        val json = OpenAiRequestBuilder.buildStreamingRequestJson(
            provider = provider,
            instruction = null,
            direction = "studio portrait",
            promptCount = 5,
            reasoningEffort = ReasoningEffort.HIGH,
            activeTriggerWord = "ohwx woman,",
        )
        val parsed = SimpleJsonParser(json).parseValue() as Map<*, *>
        assertEquals("high", parsed["reasoning_effort"])

        val messages = parsed["messages"] as List<*>
        val systemContent = (messages[0] as Map<*, *>)["content"] as String
        assertTrue(systemContent.contains("ohwx woman"))
        assertTrue(systemContent.contains("do NOT repeat"))
    }
}
