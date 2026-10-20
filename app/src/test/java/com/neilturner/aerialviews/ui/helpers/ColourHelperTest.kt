package com.neilturner.aerialviews.ui.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ColourHelperTest {
    @Test
    fun `parses six digit hex as opaque`() {
        assertEquals(0xFFFFFFFF.toInt(), ColourHelper.parseOpaqueColour("#FFFFFF"))
        assertEquals(0xFF000000.toInt(), ColourHelper.parseOpaqueColour("#000000"))
        assertEquals(0xFFFFC107.toInt(), ColourHelper.parseOpaqueColour("#FFC107"))
        assertEquals(0xFF2196F3.toInt(), ColourHelper.parseOpaqueColour("#2196f3"))
    }

    @Test
    fun `expands three digit hex`() {
        assertEquals(0xFFFF0000.toInt(), ColourHelper.parseOpaqueColour("#f00"))
        assertEquals(0xFFFFFFFF.toInt(), ColourHelper.parseOpaqueColour("#FFF"))
        assertEquals(0xFF00FF00.toInt(), ColourHelper.parseOpaqueColour("#0f0"))
    }

    @Test
    fun `discards alpha from eight digit hex`() {
        assertEquals(0xFFFF0000.toInt(), ColourHelper.parseOpaqueColour("#80FF0000"))
        assertEquals(0xFF123456.toInt(), ColourHelper.parseOpaqueColour("#00123456"))
    }

    @Test
    fun `parses basic colour names regardless of case`() {
        assertEquals(0xFFFFFFFF.toInt(), ColourHelper.parseOpaqueColour("white"))
        assertEquals(0xFFFFFFFF.toInt(), ColourHelper.parseOpaqueColour("WHITE"))
        assertEquals(0xFF000000.toInt(), ColourHelper.parseOpaqueColour("Black"))
        assertEquals(0xFF000080.toInt(), ColourHelper.parseOpaqueColour("navy"))
        assertEquals(0xFF00FFFF.toInt(), ColourHelper.parseOpaqueColour("aqua"))
    }

    @Test
    fun `accepts a bare hex value without the hash`() {
        assertEquals(0xFF2196F3.toInt(), ColourHelper.parseOpaqueColour("2196F3"))
        assertEquals(0xFFFF0000.toInt(), ColourHelper.parseOpaqueColour("f00"))
        assertEquals(0xFFFF0000.toInt(), ColourHelper.parseOpaqueColour("80FF0000"))
    }

    @Test
    fun `rejects unusable values`() {
        assertNull(ColourHelper.parseOpaqueColour(null))
        assertNull(ColourHelper.parseOpaqueColour(""))
        assertNull(ColourHelper.parseOpaqueColour("   "))
        assertNull(ColourHelper.parseOpaqueColour("rebeccapurple"))
        assertNull(ColourHelper.parseOpaqueColour("#GGGGGG"))
        assertNull(ColourHelper.parseOpaqueColour("#FFFFF"))
        assertNull(ColourHelper.parseOpaqueColour("0xFFFFFF"))
        assertNull(ColourHelper.parseOpaqueColour("12345"))
    }

    @Test
    fun `hex string round trips`() {
        listOf("#FFFFFF", "#000000", "#FFC107", "#2196F3", "#4CAF50").forEach { hex ->
            val parsed = ColourHelper.parseOpaqueColour(hex)
            assertEquals(hex, ColourHelper.toHexString(parsed!!))
        }
    }
}
