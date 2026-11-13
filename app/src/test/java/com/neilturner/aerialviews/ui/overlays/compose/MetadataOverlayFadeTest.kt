package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.theme.AerialTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The POI line's change sequence, checked against Compose's own clock.
 *
 * The View fades the line out to nothing, swaps the text while it is invisible, then fades it back
 * in - a second each way. Until now only a screen recording checked that; this drives the real
 * composable and reads the alpha it is drawing at, plus the text swap from the semantics.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application rather than AerialApp: the real one touches prefs and Firebase before the
// test can initialise Kotpref
@Config(sdk = [34], application = android.app.Application::class)
class MetadataOverlayFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private val first = "Piccadilly Circus"
    private val second = "Trafalgar Square"

    /** Where the "player" is; past the second POI from the start, so the first check rotates. */
    private val position = mutableStateOf(8_000L)

    private val state =
        MetadataOverlayState(
            text = first,
            poi = mapOf(0 to first, 8 to second),
            metadataType = MetadataType.DYNAMIC,
        )

    /** Waits released before the poll is parked; the first check is the only one this test wants. */
    private var permits = 1

    /** The same fade the overlay would create for itself, held here so its alpha can be read. */
    private val fade = OverlayFade(POI_FADE_MILLIS)

    // Frames land on the test clock's 16ms boundary, so a value read after advancing is within a
    // frame of the exact one.
    private val frameTolerance = 0.06f

    // The line reads its colour, size and padding preferences directly, so Kotpref has to be
    // usable. It is normally initialised by its ContentProvider.
    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `a poi change fades out, swaps while blank and fades back in over two seconds`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AerialTheme {
                MetadataOverlay(
                    state = state,
                    sizeSp = 18f,
                    weight = "300",
                    videoPosition = { position.value },
                    fadingOutMedia = false,
                    // The poll's own waits are real time, so the first check is released here and
                    // the loop is parked afterwards: a no-op wait would spin it.
                    waitForNextCheck = { if (permits-- > 0) Unit else awaitCancellation() },
                    fade = fade,
                )
            }
        }
        // Runs the released check; the position is past the second POI, so the fade-out begins.
        compose.waitForIdle()

        // The old text is still there and still at full brightness as the fade begins.
        compose.onNodeWithText(first).assertExists()
        assertEquals(1f, fade.alpha.value, frameTolerance)

        // Half a second out: the tween is halfway, and the text has not been swapped yet.
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0.5f, fade.alpha.value, 0.1f)
        compose.onNodeWithText(first).assertExists()
        compose.onNodeWithText(second).assertDoesNotExist()

        // One second in: the fade-out has finished. The swap itself is applied on the next frame,
        // at zero alpha - the View's blank gap rather than a crossfade of the two strings.
        compose.mainClock.advanceTimeBy(500)
        assertTrue("the fade-out ran past a second", fade.alpha.value < 0.05f)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(first).assertDoesNotExist()
        compose.onNodeWithText(second).assertExists()
        assertTrue("the swap was not blank", fade.alpha.value < 0.05f)

        // Halfway back in.
        compose.mainClock.advanceTimeBy(500)
        assertEquals(0.5f, fade.alpha.value, 0.1f)

        // A second later the fade-in has finished: two seconds from the change, one each way.
        compose.mainClock.advanceTimeBy(500)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }
}
