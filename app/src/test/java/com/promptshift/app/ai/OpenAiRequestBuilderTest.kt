package com.promptshift.app.ai

import com.promptshift.app.data.SimpleJsonParser
import com.promptshift.app.domain.InstructionProfile
import com.promptshift.app.domain.PromptFormatGuide
import com.promptshift.app.domain.ProviderProfile
import org.junit.Assert.assertEquals
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
}
