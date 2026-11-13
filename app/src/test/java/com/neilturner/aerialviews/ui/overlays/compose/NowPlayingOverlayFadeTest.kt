package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.enums.NowPlayingFormat
import com.neilturner.aerialviews.services.MusicEvent
import com.neilturner.aerialviews.ui.overlays.state.NowPlayingOverlayState
import com.neilturner.aerialviews.ui.theme.AerialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The now playing line's track change, checked against Compose's own clock.
 *
 * The View fades the line out, swaps track and art while it is invisible, then fades it back in -
 * 300ms each way. Spotify's own buffering gap between tracks also blanks the line, so a device
 * recording cannot separate the two; this drives the direct track-to-track path.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application rather than AerialApp: the real one touches prefs and Firebase before the
// test can initialise Kotpref
@Config(sdk = [34], application = android.app.Application::class)
class NowPlayingOverlayFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = mutableStateOf(NowPlayingOverlayState(MusicEvent(artist = "A", song = "One")))

    /** The same fade the overlay would create for itself, held here so its alpha can be read. */
    private val fade = OverlayFade(NOW_PLAYING_FADE_MILLIS)

    private val frameTolerance = 0.06f

    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AerialTheme {
                NowPlayingOverlay(
                    state = state.value,
                    format = NowPlayingFormat.SONG_ARTIST,
                    sizeSp = 18f,
                    weight = "300",
                    fade = fade,
                )
            }
        }
        compose.mainClock.advanceTimeBy(16)
    }

    private fun nextTrack() = compose.runOnUiThread { state.value = NowPlayingOverlayState(MusicEvent(artist = "B", song = "Two")) }

    @Test
    fun `a track change fades out, swaps while blank and fades back in over 600ms`() {
        show()

        compose.onNodeWithText("One · A").assertExists()
        assertEquals(1f, fade.alpha.value, frameTolerance)

        nextTrack()
        // Lets the new state reach the composable, then one frame to start the tween.
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()

        // 150ms out: halfway, still the old track.
        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)
        compose.onNodeWithText("One · A").assertExists()
        compose.onNodeWithText("Two · B").assertDoesNotExist()

        // 300ms in: the old line is blank, and the new one takes its place at zero alpha.
        compose.mainClock.advanceTimeBy(150)
        assertTrue("the fade-out ran past 300ms", fade.alpha.value < 0.05f)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("One · A").assertDoesNotExist()
        compose.onNodeWithText("Two · B").assertExists()
        assertTrue("the swap was not blank", fade.alpha.value < 0.05f)

        // Halfway back in.
        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)

        // 600ms after the change: fully visible again.
        compose.mainClock.advanceTimeBy(150)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }
}
