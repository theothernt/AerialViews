package com.neilturner.aerialviews.models.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

internal class EnumLogicTest {
    @Test
    fun `time of day parses every known value case insensitively`() {
        assertEquals(TimeOfDay.DAY, TimeOfDay.fromString("day"))
        assertEquals(TimeOfDay.NIGHT, TimeOfDay.fromString("NIGHT"))
        assertEquals(TimeOfDay.SUNSET, TimeOfDay.fromString("Sunset"))
        assertEquals(TimeOfDay.SUNRISE, TimeOfDay.fromString("Sunrise"))
    }

    @Test
    fun `time of day falls back to unknown for absent or unrecognised values`() {
        assertEquals(TimeOfDay.UNKNOWN, TimeOfDay.fromString(null))
        assertEquals(TimeOfDay.UNKNOWN, TimeOfDay.fromString(""))
        assertEquals(TimeOfDay.UNKNOWN, TimeOfDay.fromString("dusk"))
    }

    @Test
    fun `scene type parses every known value case insensitively`() {
        val expected =
            mapOf(
                "nature" to SceneType.NATURE,
                "beach" to SceneType.BEACH,
                "countryside" to SceneType.COUNTRYSIDE,
                "waterfall" to SceneType.WATERFALL,
                "city" to SceneType.CITY,
                "sea" to SceneType.SEA,
                "space" to SceneType.SPACE,
                "patterns" to SceneType.PATTERNS,
                "fire" to SceneType.FIRE,
            )
        expected.forEach { (value, scene) ->
            assertEquals(scene, SceneType.fromString(value), "lowercase: $value")
            assertEquals(scene, SceneType.fromString(value.uppercase(Locale.ROOT)), "uppercase: $value")
        }
    }

    @Test
    fun `scene type falls back to unknown for absent or unrecognised values`() {
        assertEquals(SceneType.UNKNOWN, SceneType.fromString(null))
        assertEquals(SceneType.UNKNOWN, SceneType.fromString(""))
        assertEquals(SceneType.UNKNOWN, SceneType.fromString("mountain"))
    }

    @Test
    fun `parsing does not depend on the device locale`() {
        val previous = Locale.getDefault()
        try {
            // Turkish lowercases 'I' to a dotless 'i', which would break NIGHT and CITY.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(TimeOfDay.NIGHT, TimeOfDay.fromString("NIGHT"))
            assertEquals(SceneType.CITY, SceneType.fromString("CITY"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `aspect ratio is derived from the larger dimension`() {
        assertEquals(AspectRatio.LANDSCAPE, AspectRatio.fromDimensions(1920, 1080))
        assertEquals(AspectRatio.PORTRAIT, AspectRatio.fromDimensions(1080, 1920))
        assertEquals(AspectRatio.SQUARE, AspectRatio.fromDimensions(1080, 1080))
    }

    @Test
    fun `aspect ratio handles zero dimensions`() {
        assertEquals(AspectRatio.SQUARE, AspectRatio.fromDimensions(0, 0))
        assertEquals(AspectRatio.LANDSCAPE, AspectRatio.fromDimensions(1, 0))
        assertEquals(AspectRatio.PORTRAIT, AspectRatio.fromDimensions(0, 1))
    }

    @Test
    fun `network sources are exactly the remote source set`() {
        val networkSources =
            setOf(
                AerialMediaSource.APPLE,
                AerialMediaSource.COMM1,
                AerialMediaSource.COMM2,
                AerialMediaSource.RTSP,
                AerialMediaSource.AMAZON,
                AerialMediaSource.CUSTOM,
                AerialMediaSource.HLS,
                AerialMediaSource.IMMICH,
                AerialMediaSource.NCMEMORIES,
            )

        // Exhaustive over every enum entry, so adding a source without updating the set fails.
        AerialMediaSource.entries.forEach { source ->
            assertEquals(source in networkSources, source.requiresNetwork, source.name)
        }
    }

    @Test
    fun `unknown and local sources are never treated as network sources`() {
        assertFalse(AerialMediaSource.UNKNOWN.requiresNetwork)
        assertFalse(AerialMediaSource.LOCAL.requiresNetwork)
        assertFalse(AerialMediaSource.SAMBA.requiresNetwork)
        assertFalse(AerialMediaSource.WEBDAV.requiresNetwork)
        assertTrue(AerialMediaSource.IMMICH.requiresNetwork)
    }
}
