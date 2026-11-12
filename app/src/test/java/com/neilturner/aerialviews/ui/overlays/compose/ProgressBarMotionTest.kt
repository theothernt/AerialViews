package com.neilturner.aerialviews.ui.overlays.compose

import com.neilturner.aerialviews.ui.controls.ProgressState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The progress bar's maths, kept out of the animation effect so it can be checked without Compose
 * clock, Robolectric or a device.
 */
class ProgressBarMotionTest {
    @Test
    fun `fraction is the position over the duration`() {
        assertEquals(0f, progressFraction(0, 1_000))
        assertEquals(0.25f, progressFraction(250, 1_000))
        assertEquals(0.5f, progressFraction(500, 1_000))
        assertEquals(1f, progressFraction(1_000, 1_000))
    }

    @Test
    fun `a position outside the item has no fraction`() {
        assertNull(progressFraction(-1, 1_000))
        assertNull(progressFraction(1_001, 1_000))
        assertNull(progressFraction(10, 0))
        assertNull(progressFraction(10, -5))
    }

    @Test
    fun `a long item does not lose precision`() {
        // Feature-length durations are seconds, but the maths goes through Double so a huge
        // position cannot overflow the way a Float multiply-then-divide would.
        val fraction = progressFraction(3_000_000_000L, 9_000_000_000L)
        assertEquals(0.3333333f, fraction!!, 1e-6f)
    }

    @Test
    fun `a start grows from the current position to full width over the whole duration`() {
        assertEquals(
            ProgressBarMotion.Animate(from = 0.25f, to = 1f, durationMillis = 2_000),
            progressBarMotion(ProgressState.START, 500, 2_000),
        )
    }

    @Test
    fun `a start at the beginning fills the bar over the whole duration`() {
        assertEquals(
            ProgressBarMotion.Animate(from = 0f, to = 1f, durationMillis = 800),
            progressBarMotion(ProgressState.START, 0, 800),
        )
    }

    @Test
    fun `a start without a usable position resets instead of animating`() {
        assertEquals(ProgressBarMotion.Reset, progressBarMotion(ProgressState.START, 0, 0))
        assertEquals(ProgressBarMotion.Reset, progressBarMotion(ProgressState.START, 5_000, 2_000))
        assertEquals(ProgressBarMotion.Reset, progressBarMotion(ProgressState.START, -1, 2_000))
    }

    @Test
    fun `pause resume and reset map onto their motions`() {
        assertEquals(ProgressBarMotion.Pause, progressBarMotion(ProgressState.PAUSE, 0, 0))
        assertEquals(ProgressBarMotion.Resume, progressBarMotion(ProgressState.RESUME, 0, 0))
        assertEquals(ProgressBarMotion.Reset, progressBarMotion(ProgressState.RESET, 0, 0))
    }
}
