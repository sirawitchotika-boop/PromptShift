package com.promptshift.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingPromptAssemblerTest {

    @Test
    fun `unlocks slot 1 immediately when second delimiter arrives across split chunks`() {
        val unlocked = mutableListOf<String>()
        val assembler = StreamingPromptAssembler { unlocked.add(it) }

        // Split delimiter across chunks ("===PR" + "OMPT===")
        assembler.feedTextDelta("Sure! Here are your prompts:\n===PR")
        assembler.feedTextDelta("OMPT===\nFirst cinematic scene, ")
        assembler.feedTextDelta("rainy neon reflections\n")
        assertEquals("Slot #1 should not unlock before next delimiter or finish", 0, unlocked.size)

        // Second delimiter completes Slot #1 immediately!
        assembler.feedTextDelta("===PRO")
        assembler.feedTextDelta("MPT===\nSecond prompt in progress")
        assertEquals(1, unlocked.size)
        assertEquals("First cinematic scene, rainy neon reflections", unlocked[0])

        // Finalize unlocks Slot #2
        assembler.finish()
        assertEquals(2, unlocked.size)
        assertEquals("Second prompt in progress", unlocked[1])
    }

    @Test
    fun `strips think tags across arbitrary chunk splits and markdown code fences`() {
        val unlocked = mutableListOf<String>()
        val assembler = StreamingPromptAssembler { unlocked.add(it) }

        val chunks = listOf(
            "<th",
            "ink>Planning 2 prompts silently...\n===PROMPT===\nFake reasoning prompt\n</thi",
            "nk>\n```",
            "text\n===PROMPT===\n",
            "Clean prompt one\n===PROMPT===\n",
            "Clean prompt two\n```",
        )
        chunks.forEach { assembler.feedTextDelta(it) }
        assembler.finish()

        assertEquals(listOf("Clean prompt one", "Clean prompt two"), unlocked)
    }

    @Test
    fun `premature cancel without finish keeps only completed slots`() {
        val unlocked = mutableListOf<String>()
        val assembler = StreamingPromptAssembler { unlocked.add(it) }

        assembler.feedTextDelta("===PROMPT===\nCompleted slot 1\n===PROMPT===\nCompleted slot 2\n===PROMPT===\nHalf streamed slot 3")
        // Do NOT call finish() — simulates user tapping Stop mid-stream
        assertEquals(listOf("Completed slot 1", "Completed slot 2"), unlocked)
    }

    @Test
    fun `parses OpenAI SSE lines and strictly ignores reasoning_content`() {
        val reasoningLine =
            """data: {"choices":[{"delta":{"reasoning_content":"secret chain of thought","content":null}}]}"""
        assertEquals(SseEvent.Ignore, StreamingPromptAssembler.parseSseLine(reasoningLine))

        val deltaLine =
            """data: {"choices":[{"delta":{"content":"===PROMPT===\nHello \"GT\" world"}}]}"""
        assertEquals(
            SseEvent.ContentDelta("===PROMPT===\nHello \"GT\" world"),
            StreamingPromptAssembler.parseSseLine(deltaLine),
        )

        assertEquals(SseEvent.Done, StreamingPromptAssembler.parseSseLine("data: [DONE]"))
        assertEquals(SseEvent.Ignore, StreamingPromptAssembler.parseSseLine(": keep-alive"))

        val errLine = """data: {"error":{"message":"Insufficient quota"}}"""
        assertEquals(SseEvent.Error("Insufficient quota"), StreamingPromptAssembler.parseSseLine(errLine))
    }

    @Test
    fun `formats HTTP error codes clearly with provider error message`() {
        val err401 = HttpOpenAiStreamingClient.formatHttpError(
            401,
            """{"error":{"message":"Incorrect API key provided"}}""",
        )
        assertTrue(err401.contains("401"))
        assertTrue(err401.contains("Incorrect API key provided"))

        val err429 = HttpOpenAiStreamingClient.formatHttpError(429, "")
        assertTrue(err429.contains("429"))
    }
}
