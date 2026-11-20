package com.promptshift.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTextComposerTest {

    @Test
    fun `normalizes trigger words and joins without double commas`() {
        assertEquals("ohwx woman", PromptTextComposer.normalizeTriggerWord("  ohwx woman,  "))
        assertNull(PromptTextComposer.normalizeTriggerWord(" , , "))

        assertEquals(
            "ohwx woman, cinematic portrait in neon rain",
            PromptTextComposer.applyTriggerWord("cinematic portrait in neon rain", "ohwx woman,"),
        )
        assertEquals(
            "ohwx woman, cinematic portrait",
            PromptTextComposer.applyTriggerWord(", cinematic portrait", "ohwx woman,, "),
        )
        assertEquals(
            "cinematic portrait",
            PromptTextComposer.applyTriggerWord("  cinematic portrait  ", null),
        )
    }

    @Test
    fun `2000-char counter syncs with active trigger word plus separator`() {
        val body = "a".repeat(1990)
        val state = PromptBatchState(prompts = listOf(body), currentIndex = 0)

        // Without trigger word: 1990/2000 (within quota)
        assertEquals("1990/2000", state.charCountLabel(null))
        assertFalse(state.isCurrentExceedingQuota(null))

        // With 10-char trigger word + ", " (2 chars) -> 2002/2000 (exceeds quota!)
        val trigger = "ohwx woman"
        assertEquals(2002, state.effectiveCharCount(trigger))
        assertEquals("2002/2000", state.charCountLabel(trigger))
        assertTrue(state.isCurrentExceedingQuota(trigger))
    }

    @Test
    fun `smart snippet join avoids leading comma on empty box and double commas`() {
        assertEquals("8k detailed", PromptTextComposer.formatSmartJoin("", "8k detailed"))
        assertEquals("8k detailed", PromptTextComposer.formatSmartJoin("   ", "8k detailed"))
        assertEquals(", 8k detailed", PromptTextComposer.formatSmartJoin("portrait", "8k detailed"))
        assertEquals(" 8k detailed", PromptTextComposer.formatSmartJoin("portrait,", "8k detailed"))
        assertEquals("8k detailed", PromptTextComposer.formatSmartJoin("portrait, ", "8k detailed"))
        assertEquals("8k detailed", PromptTextComposer.formatSmartJoin("line1\n", "8k detailed"))

        assertEquals("portrait, 8k detailed", PromptTextComposer.appendToDraft("portrait", "8k detailed"))
        assertEquals("8k detailed", PromptTextComposer.appendToDraft("", "8k detailed"))
    }
}
