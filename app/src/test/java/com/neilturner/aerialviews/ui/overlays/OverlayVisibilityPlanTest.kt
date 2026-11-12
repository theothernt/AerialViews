package com.neilturner.aerialviews.ui.overlays

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The per-mode targets. Both engines apply these, so a mode that describes itself wrongly here is
 * wrong on the TV in both engines at once rather than in one of them.
 */
class OverlayVisibilityPlanTest {
    private val opacity = 0.85f

    @Test
    fun `always visible starts at the overlay opacity and never changes`() {
        val plan = overlayVisibilityPlan(MODE_ALWAYS_VISIBLE, opacity)

        assertEquals(visibleOverlayTarget(opacity), plan.start)
        assertNull(plan.afterDelay)
        assertTrue(plan.revealsWhileWaiting)
    }

    @Test
    fun `always hidden starts down and never changes`() {
        val plan = overlayVisibilityPlan(MODE_ALWAYS_HIDDEN, opacity)

        assertEquals(hiddenOverlayTarget, plan.start)
        assertNull(plan.afterDelay)
        assertTrue(plan.revealsWhileWaiting)
    }

    @Test
    fun `hide after delay starts up and ends down`() {
        val plan = overlayVisibilityPlan(MODE_HIDE_AFTER_DELAY, opacity)

        assertEquals(visibleOverlayTarget(opacity), plan.start)
        assertEquals(hiddenOverlayTarget, plan.afterDelay)
        assertTrue(plan.revealsWhileWaiting)
    }

    @Test
    fun `show after delay starts down and ends up`() {
        val plan = overlayVisibilityPlan(MODE_SHOW_AFTER_DELAY, opacity)

        assertEquals(hiddenOverlayTarget, plan.start)
        assertEquals(visibleOverlayTarget(opacity), plan.afterDelay)
        // A reveal would race the transition that is still to come
        assertFalse(plan.revealsWhileWaiting)
    }

    @Test
    fun `the two delayed modes are each other's opposite`() {
        val hide = overlayVisibilityPlan(MODE_HIDE_AFTER_DELAY, opacity)
        val show = overlayVisibilityPlan(MODE_SHOW_AFTER_DELAY, opacity)

        assertEquals(show.afterDelay, hide.start)
        assertEquals(hide.afterDelay, show.start)
    }

    @Test
    fun `an unrecognised mode settles hidden and stays there`() {
        val plan = overlayVisibilityPlan("SOMETHING_NEW", opacity)

        assertEquals(hiddenOverlayTarget, plan.start)
        assertNull(plan.afterDelay)
    }

    @Test
    fun `the gradient follows the overlays in every mode`() {
        val modes = listOf(MODE_ALWAYS_VISIBLE, MODE_ALWAYS_HIDDEN, MODE_HIDE_AFTER_DELAY, MODE_SHOW_AFTER_DELAY)

        modes.forEach { mode ->
            val plan = overlayVisibilityPlan(mode, opacity)
            listOfNotNull(plan.start, plan.afterDelay).forEach { target ->
                assertEquals(
                    if (target.alpha > 0f) 1f else 0f,
                    target.gradient,
                    "gradient must be up exactly when the overlays are, in $mode",
                )
            }
        }
    }
}
