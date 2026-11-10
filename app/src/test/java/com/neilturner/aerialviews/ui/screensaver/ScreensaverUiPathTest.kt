package com.neilturner.aerialviews.ui.screensaver

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ScreensaverUiPathTest {
    @Test
    fun `parses both supported paths case-insensitively`() {
        assertEquals(ScreensaverUiPath.LEGACY, ScreensaverUiPath.parse("legacy"))
        assertEquals(ScreensaverUiPath.LEGACY, ScreensaverUiPath.parse("LEGACY"))
        assertEquals(ScreensaverUiPath.COMPOSE, ScreensaverUiPath.parse("compose"))
        assertEquals(ScreensaverUiPath.COMPOSE, ScreensaverUiPath.parse("Compose"))
    }

    @Test
    fun `returns null for missing or unknown overrides`() {
        assertNull(ScreensaverUiPath.parse(null))
        assertNull(ScreensaverUiPath.parse(""))
        assertNull(ScreensaverUiPath.parse("other"))
    }
}
