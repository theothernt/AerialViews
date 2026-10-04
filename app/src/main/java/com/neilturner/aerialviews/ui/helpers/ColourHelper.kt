package com.neilturner.aerialviews.ui.helpers

import android.graphics.Color
import androidx.core.graphics.toColorInt

object ColourHelper {
    const val DEFAULT_OVERLAY_COLOUR_HEX = "#FFFFFF"

    private const val OPAQUE_MASK = 0xFF000000.toInt()

    // Lowercase basic colour names accepted by Color.parseColor, so parsing stays pure Kotlin
    // (app/build.gradle.kts sets no testOptions, so unit tests cannot touch android.graphics.Color).
    private val namedColours =
        mapOf(
            "black" to 0x000000,
            "white" to 0xFFFFFF,
            "red" to 0xFF0000,
            "green" to 0x008000,
            "lime" to 0x00FF00,
            "blue" to 0x0000FF,
            "yellow" to 0xFFFF00,
            "cyan" to 0x00FFFF,
            "aqua" to 0x00FFFF,
            "magenta" to 0xFF00FF,
            "fuchsia" to 0xFF00FF,
            "gray" to 0x808080,
            "grey" to 0x808080,
            "lightgray" to 0xD3D3D3,
            "lightgrey" to 0xD3D3D3,
            "darkgray" to 0xA9A9A9,
            "darkgrey" to 0xA9A9A9,
            "silver" to 0xC0C0C0,
            "maroon" to 0x800000,
            "navy" to 0x000080,
            "olive" to 0x808000,
            "purple" to 0x800080,
            "teal" to 0x008080,
        )

    fun colourFromString(colourString: String): Int =
        try {
            colourString.toColorInt()
        } catch (e: IllegalArgumentException) {
            Color.BLACK // Default if parsing fails
        }

    /**
     * Parses `#RGB`, `#RRGGBB` or `#AARRGGBB` plus basic colour names into an opaque 0xAARRGGBB int.
     * The `#` is optional when typing a bare hex value. Any alpha component is discarded because
     * overlay opacity is controlled by its own setting. Returns null when the value cannot be parsed.
     */
    fun parseOpaqueColour(value: String?): Int? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null

        if (!trimmed.startsWith("#")) {
            namedColours[trimmed.lowercase()]?.let { return OPAQUE_MASK or it }
            // Allow a bare hex value such as 2196F3
            return parseHexDigits(trimmed)
        }

        return parseHexDigits(trimmed.substring(1))
    }

    private fun parseHexDigits(digits: String): Int? {
        if (!digits.all { it.isHexDigit() }) return null

        val rgb =
            when (digits.length) {
                3 -> expandShortHex(digits)

                6 -> digits.toIntOrNull(16)

                8 -> digits.substring(2).toIntOrNull(16)

                // drop the leading alpha pair
                else -> null
            } ?: return null

        return OPAQUE_MASK or rgb
    }

    // Overlay text must stay visible, so anything unparseable falls back to white, not black.
    fun overlayColour(value: String?): Int = parseOpaqueColour(value) ?: Color.WHITE

    fun toHexString(colour: Int): String = String.format("#%06X", 0xFFFFFF and colour)

    private fun expandShortHex(digits: String): Int? {
        val expanded = digits.map { "$it$it" }.joinToString("")
        return expanded.toIntOrNull(16)
    }

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
}
