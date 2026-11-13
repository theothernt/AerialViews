package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.theme.AerialTheme
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The POI line's `fadingOutMedia` guard, checked one poll at a time.
 *
 * The View holds the text still while the item fades out, so a POI that falls due in that window is
 * not faded under the loading view; the next item's metadata clears the flag and the held change
 * goes ahead. The flag flips after the poll has started, which is the case a captured parameter gets
 * wrong - so the poll is driven through [MetadataOverlay]'s `waitForNextCheck` hook, one released
 * check at a time, rather than by waiting out its real-time `delay`.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application rather than AerialApp: the real one touches prefs and Firebase before the
// test can initialise Kotpref
@Config(sdk = [34], application = android.app.Application::class)
class MetadataOverlayHoldTest {
    @get:Rule
    val compose = createComposeRule()

    private val first = "Piccadilly Circus"
    private val second = "Trafalgar Square"
    private val third = "Tower Bridge"

    private val position = mutableStateOf(8_000L)

    /** False at composition, set true later, as the controller does on the item's fade-out. */
    private val fadingOutMedia = mutableStateOf(false)

    private val state =
        MetadataOverlayState(
            text = first,
            poi = mapOf(0 to first, 8 to second, 16 to third),
            metadataType = MetadataType.DYNAMIC,
        )

    /** The same fade the overlay would create for itself, held here so its alpha can be read. */
    private val fade = OverlayFade(POI_FADE_MILLIS)

    /** One permit per poll check; a check runs only when the test releases it. */
    private val gate = Channel<Unit>(Channel.RENDEZVOUS)

    private val frameTolerance = 0.06f

    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private fun composeOverlay() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AerialTheme {
                MetadataOverlay(
                    state = state,
                    sizeSp = 18f,
                    weight = "300",
                    videoPosition = { position.value },
                    fadingOutMedia = fadingOutMedia.value,
                    pollMillis = 0,
                    settleMillis = 0,
                    waitForNextCheck = { gate.receive() },
                    fade = fade,
                )
            }
        }
        compose.waitForIdle()
    }

    /** Releases one poll check and lets it run until it suspends again. */
    private fun releaseCheck() {
        assertTrue("no poll was waiting for a check", gate.trySend(Unit).isSuccess)
        compose.waitForIdle()
    }

    /**
     * Applies any pending state change to the composition before the next check reads it. One frame
     * is not enough: the write invalidates the composition during that frame, so the new parameter
     * only reaches `rememberUpdatedState` on the one after.
     */
    private fun pumpRecomposition() {
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    /** Advances [frames] frames, returning the lowest alpha drawn along the way. */
    private fun advanceFrames(frames: Int): Float {
        var lowest = 1f
        repeat(frames) {
            // Each frame's continuation is dispatched to the main looper, so the clock has to be
            // pumped as well as advanced for the alpha to move at all.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            lowest = minOf(lowest, fade.alpha.value)
        }
        return lowest
    }

    @Test(timeout = 60_000)
    fun `a poi change is held while the media fades out and lands once it clears`() {
        composeOverlay()

        // First check: the position is past the second POI, so the change goes ahead.
        releaseCheck()
        advanceFrames(150)
        compose.onNodeWithText(second).assertExists()
        assertEquals(1f, fade.alpha.value, frameTolerance)

        // The item starts leaving just as the next POI falls due.
        compose.runOnUiThread {
            fadingOutMedia.value = true
            position.value = 16_000L
        }
        pumpRecomposition()
        releaseCheck()

        // Held: no swap, and nothing fades, however much time passes.
        assertEquals("the line faded while the item was leaving", 1f, advanceFrames(120), frameTolerance)
        compose.onNodeWithText(second).assertExists()
        compose.onNodeWithText(third).assertDoesNotExist()

        // The next item's metadata clears the guard, and the held change goes ahead - all the way
        // through the fade and the swap.
        compose.runOnUiThread { fadingOutMedia.value = false }
        pumpRecomposition()
        releaseCheck()

        assertTrue("the held change never faded", advanceFrames(150) < 0.05f)
        compose.onNodeWithText(third).assertExists()
        compose.onNodeWithText(second).assertDoesNotExist()
    }

    @Test(timeout = 60_000)
    fun `a poi change that falls due while the item is already leaving is held too`() {
        // The flag is true before the poll ever runs, which is what a captured parameter would
        // have handled by luck.
        fadingOutMedia.value = true
        composeOverlay()
        releaseCheck()

        assertEquals("the line faded while the item was leaving", 1f, advanceFrames(120), frameTolerance)
        compose.onNodeWithText(first).assertExists()
        compose.onNodeWithText(second).assertDoesNotExist()
    }
}
