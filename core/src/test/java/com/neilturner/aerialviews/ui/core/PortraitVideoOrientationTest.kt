package com.neilturner.aerialviews.ui.core

import com.neilturner.aerialviews.models.enums.VideoScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Characterisation tests for [PortraitVideoOrientation] as originally written.
 *
 * These lock in the behaviour that shipped, so the extraction can be proven
 * faithful before the scaling bug is changed.
 */
internal class PortraitVideoOrientationTest {
    private val containerW = 1920f
    private val containerH = 1080f

    private fun resolve(
        videoW: Int = 1080,
        videoH: Int = 1920,
        degrees: Float = 90f,
        scale: VideoScale? = VideoScale.SCALE_TO_FIT,
        containerRotation: Int? = 0,
    ) = PortraitVideoOrientation.resolve(
        videoWidth = videoW,
        videoHeight = videoH,
        containerRotationDegrees = containerRotation,
        enabledDegrees = degrees,
        videoScale = scale,
        containerWidth = containerW,
        containerHeight = containerH,
    )

    @Test
    fun `parses the stored preference value`() {
        assertEquals(90f, PortraitVideoOrientation.parseDegrees("90"))
        assertEquals(270f, PortraitVideoOrientation.parseDegrees("270"))
        assertEquals(0f, PortraitVideoOrientation.parseDegrees("0"))
    }

    @Test
    fun `falls back to disabled for unusable preference values`() {
        assertEquals(0f, PortraitVideoOrientation.parseDegrees(null))
        assertEquals(0f, PortraitVideoOrientation.parseDegrees(""))
        assertEquals(0f, PortraitVideoOrientation.parseDegrees("nonsense"))
    }

    @Test
    fun `rotates portrait video by the requested amount`() {
        assertEquals(90f, resolve(degrees = 90f)?.rotation)
        assertEquals(270f, resolve(degrees = 270f)?.rotation)
        assertEquals(180f, resolve(degrees = 180f)?.rotation)
    }

    @Test
    fun `scales by the container ratio for quarter turns`() {
        assertEquals(containerW / containerH, resolve(degrees = 90f)?.scale)
        assertEquals(containerW / containerH, resolve(degrees = 270f)?.scale)
    }

    @Test
    fun `does not scale for a half turn`() {
        assertEquals(1f, resolve(degrees = 180f)?.scale)
    }

    @Test
    fun `leaves landscape video alone`() {
        assertNull(resolve(videoW = 1920, videoH = 1080))
        assertNull(resolve(videoW = 1920, videoH = 1080, degrees = 180f))
    }

    @Test
    fun `leaves video alone while the fix is disabled`() {
        assertNull(resolve(degrees = 0f))
    }

    @Test
    fun `leaves video alone until the container has been measured`() {
        assertNull(
            PortraitVideoOrientation.resolve(
                videoWidth = 1080,
                videoHeight = 1920,
                containerRotationDegrees = 0,
                enabledDegrees = 90f,
                videoScale = VideoScale.SCALE_TO_FIT,
                containerWidth = 0f,
                containerHeight = 0f,
            ),
        )
    }

    @Test
    fun `ignores zero sized video reported before the first frame decodes`() {
        assertNull(resolve(videoW = 0, videoH = 0))
    }

    @Test
    fun `skips the fix when cropping is selected`() {
        assertNull(resolve(degrees = 90f, scale = VideoScale.SCALE_TO_FIT_WITH_CROPPING))
        assertNull(resolve(degrees = 180f, scale = VideoScale.SCALE_TO_FIT_WITH_CROPPING))
    }

    @Test
    fun `applies the fix when fitting without cropping`() {
        assertEquals(90f, resolve(degrees = 90f, scale = VideoScale.SCALE_TO_FIT)?.rotation)
    }

    @Test
    fun `applies the fix when the container carries no rotation`() {
        assertEquals(90f, resolve(containerRotation = 0)?.rotation)
    }

    @Test
    fun `leaves video alone when the container already carries a rotation`() {
        assertNull(resolve(containerRotation = 90))
        assertNull(resolve(containerRotation = 180))
        assertNull(resolve(containerRotation = 270))
    }

    @Test
    fun `leaves video alone while the container rotation is unknown`() {
        assertNull(resolve(containerRotation = null))
    }

    @Test
    fun `leaves landscape shaped video alone`() {
        assertNull(resolve(videoW = 1920, videoH = 1080))
        assertNull(resolve(videoW = 1920, videoH = 1080, degrees = 180f))
    }
}
