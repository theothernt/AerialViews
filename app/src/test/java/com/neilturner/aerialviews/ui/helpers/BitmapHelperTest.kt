package com.neilturner.aerialviews.ui.helpers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class BitmapHelperTest {
    @Test
    fun `sanitize exif description keeps normal caption`() {
        val description = BitmapHelper.sanitizeExifDescription(" Birthday at the beach ")

        assertEquals("Birthday at the beach", description)
    }

    @Test
    fun `sanitize exif description rejects reported vendor metadata`() {
        val description =
            BitmapHelper.sanitizeExifDescription(
                "format: 0; filter: null; filterIntensity: 0; captureOrientation: 0; " +
                    "mode: portrait-hw-remosaic false; touch: (-1.0, -1.0); " +
                    "sceneMode: 13107200; cct_value: 0; AI Scene: (-1, weatherInfo: weather?Cloudy, icon:1)",
            )

        assertNull(description)
    }

    @Test
    fun `sanitize exif description rejects long structured metadata`() {
        val description =
            BitmapHelper.sanitizeExifDescription(
                "format: 0; filter: null; orientation: 0; brightness: 1; contrast: 2; " +
                    "saturation: 3; sharpness: 4; exposure: 5; whiteBalance: auto; " +
                    "noiseReduction: enabled; detailEnhancement: enabled",
            )

        assertNull(description)
    }

    @Test
    fun `sanitize exif description rejects known vendor marker without structured payload`() {
        val description = BitmapHelper.sanitizeExifDescription("portrait-hw-remosaic")

        assertNull(description)
    }
}
