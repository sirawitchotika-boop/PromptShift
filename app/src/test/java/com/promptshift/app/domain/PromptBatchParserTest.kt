package com.promptshift.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBatchParserTest {

    @Test
    fun `parses standard multi-prompt batch and trims slot whitespace`() {
        val raw = """
            ===PROMPT===
              cinematic portrait of a cyberpunk racer, neon rain, 8k  
            ===PROMPT===

            dramatic studio lighting, obsidian carbon helmet, crimson reflections

            ===PROMPT===
            minimalist architectural render, brutalist concrete, golden hour
        """.trimIndent()

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(3, prompts.size)
        assertEquals("cinematic portrait of a cyberpunk racer, neon rain, 8k", prompts[0])
        assertEquals("dramatic studio lighting, obsidian carbon helmet, crimson reflections", prompts[1])
        assertEquals("minimalist architectural render, brutalist concrete, golden hour", prompts[2])
    }

    @Test
    fun `matches delimiter case-insensitively with surrounding spaces and tabs`() {
        val raw = "  \t===prompt===  \nFirst prompt\n\t===PrOmPt===\t\nSecond prompt\n   ===PROMPT===   \nThird prompt"

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(listOf("First prompt", "Second prompt", "Third prompt"), prompts)
    }

    @Test
    fun `strips wrapping markdown triple-backtick code fences with language tag`() {
        val raw = """
            ```text
            ===PROMPT===
            Prompt one with **style** and commas, high detail
            ===PROMPT===
            Prompt two with line
            break preserved inside slot
            ```
        """.trimIndent()

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(2, prompts.size)
        assertEquals("Prompt one with **style** and commas, high detail", prompts[0])
        assertEquals("Prompt two with line\nbreak preserved inside slot", prompts[1])
    }

    @Test
    fun `strips plain triple-backtick fences and handles CRLF line endings`() {
        val raw = "```\r\n===PROMPT===\r\nAlpha prompt\r\n===PROMPT===\r\nBeta prompt\r\n```"

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(listOf("Alpha prompt", "Beta prompt"), prompts)
    }

    @Test
    fun `extracts fenced prompt block when copied with surrounding assistant text`() {
        val raw = """
            Here are your 2 SeaArt prompts:
            ```text
            ===PROMPT===
            First creative prompt
            ===PROMPT===
            Second creative prompt
            ```
            Let me know if you want more!
        """.trimIndent()

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(listOf("First creative prompt", "Second creative prompt"), prompts)
    }

    @Test
    fun `drops empty segments between consecutive delimiters`() {
        val raw = """
            ===PROMPT===
            
            ===PROMPT===
            Valid prompt A
            ===PROMPT===
               
            ===PROMPT===
            Valid prompt B
            ===PROMPT===
        """.trimIndent()

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(listOf("Valid prompt A", "Valid prompt B"), prompts)
    }

    @Test
    fun `returns NoDelimiterFound when delimiter is missing or input is blank`() {
        assertEquals(BatchParseResult.NoDelimiterFound, PromptBatchParser.parse(null))
        assertEquals(BatchParseResult.NoDelimiterFound, PromptBatchParser.parse("   \n\t  "))
        assertEquals(
            BatchParseResult.NoDelimiterFound,
            PromptBatchParser.parse("Just a normal prompt copied from SeaArt without any delimiter."),
        )
        // Delimiter embedded mid-line is NOT a standalone delimiter line
        assertEquals(
            BatchParseResult.NoDelimiterFound,
            PromptBatchParser.parse("Use ===PROMPT=== on its own line"),
        )
    }

    @Test
    fun `returns EmptyAfterCleaning when only delimiters and whitespace exist`() {
        val raw = "===PROMPT===\n   \n===PROMPT===\n\t\n===PROMPT==="
        assertEquals(BatchParseResult.EmptyAfterCleaning, PromptBatchParser.parse(raw))
    }

    @Test
    fun `stress test parses 55k plus characters across 26 prompts without truncation`() {
        val promptCount = 26
        val singlePromptBody = buildString {
            repeat(55) { i ->
                append("ultra-detailed photorealistic scene segment $i, volumetric lighting, ")
            }
        }.trim()
        assertTrue("Each prompt should be > 2,000 chars", singlePromptBody.length > 2000)

        val fullInput = buildString {
            appendLine("```text")
            for (index in 1..promptCount) {
                appendLine("===PROMPT===")
                appendLine("SLOT_$index $singlePromptBody END_$index")
            }
            appendLine("```")
        }
        assertTrue("Total input should exceed 50,000 chars", fullInput.length > 50_000)

        val result = PromptBatchParser.parse(fullInput)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(promptCount, prompts.size)
        assertEquals("SLOT_1 $singlePromptBody END_1", prompts.first())
        assertEquals("SLOT_$promptCount $singlePromptBody END_$promptCount", prompts.last())
        // Verify delimiter is strictly excluded from cleaned character count
        assertTrue(prompts.none { it.contains("===PROMPT===") })
    }

    @Test
    fun `supports dynamic unlimited batch size of 50 plus prompts`() {
        val count = 55
        val raw = (1..count).joinToString("\n") { "===PROMPT===\nPrompt number $it" }

        val result = PromptBatchParser.parse(raw)
        assertTrue(result is BatchParseResult.Success)
        val prompts = (result as BatchParseResult.Success).prompts
        assertEquals(count, prompts.size)
        assertEquals("Prompt number 1", prompts.first())
        assertEquals("Prompt number 55", prompts.last())
    }

    @Test
    fun `built-in AI format instruction is provider-agnostic and includes delimiter`() {
        val instruction = PromptFormatGuide.AI_FORMAT_INSTRUCTION
        assertTrue(instruction.contains(PromptFormatGuide.DELIMITER))
        assertTrue(!instruction.contains("nanogpt", ignoreCase = true))
        assertTrue(PromptBatchParser.parse(PromptFormatGuide.SAMPLE_BLOCK) is BatchParseResult.Success)
    }
}
