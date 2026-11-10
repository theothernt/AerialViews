package com.neilturner.aerialviews.ui.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class OverlayOpacityHelperTest {
    @Test
    fun `alpha from percent converts selected percentage`() {
        assertEquals(1.0f, OverlayOpacityHelper.alphaFromPercent("100"))
        assertEquals(0.6f, OverlayOpacityHelper.alphaFromPercent("60"))
        assertEquals(0.1f, OverlayOpacityHelper.alphaFromPercent("10"))
    }

    @Test
    fun `alpha from percent falls back to fully visible`() {
        assertEquals(1.0f, OverlayOpacityHelper.alphaFromPercent(null))
        assertEquals(1.0f, OverlayOpacityHelper.alphaFromPercent(""))
        assertEquals(1.0f, OverlayOpacityHelper.alphaFromPercent("not-a-number"))
    }

    @Test
    fun `alpha from percent clamps out of range values`() {
        assertEquals(0.1f, OverlayOpacityHelper.alphaFromPercent("0"))
        assertEquals(1.0f, OverlayOpacityHelper.alphaFromPercent("250"))
    }

    @Test
    fun `clamp bounds stay within the offered percentage list`() {
        // R.array.overlay_opacity_values offers 10..100; a bound outside that range
        // would clamp a stored value to something the ListPreference cannot display.
        assertEquals(10, OverlayOpacityHelper.MIN_PERCENT)
        assertEquals(100, OverlayOpacityHelper.MAX_PERCENT)
    }

    @Test
    fun `overlay at visible alpha is considered visible`() {
        listOf("10", "50", "100").forEach { percent ->
            val visibleAlpha = OverlayOpacityHelper.alphaFromPercent(percent)
            assertTrue(OverlayOpacityHelper.isVisible(visibleAlpha, visibleAlpha))
        }
    }

    @Test
    fun `faded or hidden overlay is not considered visible`() {
        listOf("10", "50", "100").forEach { percent ->
            val visibleAlpha = OverlayOpacityHelper.alphaFromPercent(percent)
            assertFalse(OverlayOpacityHelper.isVisible(0f, visibleAlpha))
            assertFalse(OverlayOpacityHelper.isVisible(visibleAlpha * 0.5f, visibleAlpha))
        }
    }
}
