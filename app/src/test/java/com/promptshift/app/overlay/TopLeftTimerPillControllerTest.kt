package com.promptshift.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLeftTimerPillControllerTest {

    @Test
    fun `clampPosition keeps coordinates within portrait and landscape bounds`() {
        // Portrait (1080x2400) -> Landscape (2400x1080): y = 2100 must re-clamp inside 1080 - 96
        val (landscapeX, landscapeY) = TopLeftTimerPillController.clampPosition(
            x = 900,
            y = 2100,
            screenWidth = 2400,
            screenHeight = 1080,
            pillWidth = 200,
            pillHeight = 96,
        )
        assertEquals(900, landscapeX)
        assertEquals(984, landscapeY)

        // Landscape (2400x1080) -> Portrait (1080x2400): x = 2100 must re-clamp inside 1080 - 200
        val (portraitX, portraitY) = TopLeftTimerPillController.clampPosition(
            x = 2100,
            y = 500,
            screenWidth = 1080,
            screenHeight = 2400,
            pillWidth = 200,
            pillHeight = 96,
        )
        assertEquals(880, portraitX)
        assertEquals(500, portraitY)
    }

    @Test
    fun `clampPosition guards against negative coordinates and tiny screens`() {
        val (negX, negY) = TopLeftTimerPillController.clampPosition(
            x = -40,
            y = -15,
            screenWidth = 1080,
            screenHeight = 2400,
            pillWidth = 200,
            pillHeight = 96,
        )
        assertEquals(0, negX)
        assertEquals(0, negY)

        val (tinyX, tinyY) = TopLeftTimerPillController.clampPosition(
            x = 50,
            y = 50,
            screenWidth = 100,
            screenHeight = 50,
            pillWidth = 200,
            pillHeight = 96,
        )
        assertEquals(0, tinyX)
        assertEquals(0, tinyY)
    }

    @Test
    fun `formatPillText prefixes formatted MM SS consistently`() {
        assertEquals("⏱ 01:30", TopLeftTimerPillController.formatPillText("01:30"))
        assertEquals("⏱ 00:00", TopLeftTimerPillController.formatPillText("00:00"))
    }
}
