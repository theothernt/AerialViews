package com.neilturner.aerialviews.ui.overlays.compose

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ProgressBarLocation
import com.neilturner.aerialviews.ui.controls.ProgressState
import com.neilturner.aerialviews.ui.overlays.state.ProgressOverlayState
import com.neilturner.aerialviews.ui.theme.AerialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The progress bar's animation, checked against Compose's own clock.
 *
 * `ProgressBarMotionTest` covers the mapping from a progress event to a motion; this covers what
 * the bar actually draws afterwards, which runs through `Animatable` and a tween and therefore
 * cannot be checked without a clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ProgressBarOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    /** What the bar is fed; changing it is how a second progress event reaches the composable. */
    private val state = mutableStateOf(ProgressOverlayState())

    // Frames land on the test clock's 16ms boundary, so a fraction read after advancing is within
    // a frame of the exact value rather than equal to it.
    private val frameTolerance = 0.02f

    private fun show(initial: ProgressOverlayState) {
        state.value = initial
        // Advanced explicitly below, so the animation cannot race the assertions.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AerialTheme {
                ProgressBarOverlay(
                    state = state.value,
                    location = ProgressBarLocation.BOTTOM,
                    opacity = 1f,
                )
            }
        }
        // The first composition schedules the animation; one frame lets it start from its "from".
        compose.mainClock.advanceTimeBy(16)
    }

    private fun send(next: ProgressOverlayState) {
        compose.runOnUiThread { state.value = next }
    }

    /** The fraction the bar is drawing, read from the semantics it publishes. */
    private fun fraction(): Float =
        compose
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
            .current

    @Test
    fun `a start from the beginning fills over the item's duration`() {
        show(ProgressOverlayState(state = ProgressState.START, position = 0, duration = 4_000))

        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0.25f, fraction(), frameTolerance)

        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0.5f, fraction(), frameTolerance)

        // To the end of the item, and no further: the tween finishes at full width.
        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1f, fraction(), frameTolerance)
    }

    @Test
    fun `a start part-way through an item grows from that position`() {
        // A quarter of the way in: the bar snaps to 0.25 and then takes the whole duration again
        // to reach the end, exactly as the View's animator does.
        show(ProgressOverlayState(state = ProgressState.START, position = 1_000, duration = 4_000))
        assertEquals(0.25f, fraction(), frameTolerance)

        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0.4375f, fraction(), frameTolerance)
    }

    @Test
    fun `a start without a usable position empties the bar`() {
        show(ProgressOverlayState(state = ProgressState.START, position = 9_000, duration = 4_000))

        compose.mainClock.advanceTimeBy(500)
        assertEquals(0f, fraction(), frameTolerance)
    }

    @Test
    fun `a pause holds the fraction and a resume continues from it`() {
        show(ProgressOverlayState(state = ProgressState.START, position = 0, duration = 4_000))
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0.25f, fraction(), frameTolerance)

        // A frame after each event lets the new motion reach the composable before time moves on;
        // without it the resume only starts at the end of the window being measured.
        send(ProgressOverlayState(state = ProgressState.PAUSE, position = 0, duration = 4_000))
        compose.mainClock.advanceTimeByFrame()
        val held = fraction()
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(held, fraction(), 0.001f)

        // Resuming runs for the time that is left of the item, not the whole duration again.
        // The state change is dispatched a few frames late under the test dispatcher, so the rate
        // is measured between two later samples rather than from the moment of the event.
        send(ProgressOverlayState(state = ProgressState.RESUME, position = 0, duration = 4_000))
        compose.mainClock.advanceTimeBy(1_000)
        val first = fraction()
        compose.mainClock.advanceTimeBy(500)
        val second = fraction()
        compose.mainClock.advanceTimeBy(500)
        val third = fraction()

        // 0.75 of the bar is left and the item has 3s to run, so it covers 0.125 every 500ms.
        assertEquals(0.125f, second - first, 0.01f)
        assertEquals(0.125f, third - second, 0.01f)
        assertTrue("resume finished early at $third", third < 0.95f)

        compose.mainClock.advanceTimeBy(2_000)
        assertEquals(1f, fraction(), frameTolerance)
    }

    @Test
    fun `the bar names itself for a screen reader`() {
        show(ProgressOverlayState(state = ProgressState.START, position = 0, duration = 4_000))

        val expected =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getString(R.string.progress_bar_content_description)
        val announced =
            compose
                .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .single()

        // The wording a screen reader leads with, not just whatever the resource says.
        assertEquals("Playback progress", expected)
        assertEquals(expected, announced)
    }

    @Test
    fun `a reset empties a bar that had filled`() {
        show(ProgressOverlayState(state = ProgressState.START, position = 0, duration = 2_000))
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(0.5f, fraction(), frameTolerance)

        send(ProgressOverlayState(state = ProgressState.RESET))
        compose.mainClock.advanceTimeBy(100)
        assertEquals(0f, fraction(), frameTolerance)
    }
}
