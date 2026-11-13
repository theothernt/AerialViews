package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
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
 * The message line's two modes, checked against Compose's own clock.
 *
 * The View pops the message in and out when `message_animate_changes` is off, and fades it out,
 * swaps the message and fades it back in over 300ms each way when it is on. A device recording
 * cannot tell a 0ms swap from a 300ms one reliably, so both are driven here.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application rather than AerialApp: the real one touches prefs and Firebase before the
// test can initialise Kotpref
@Config(sdk = [34], application = android.app.Application::class)
class MessageOverlayFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = mutableStateOf(MessageOverlayState(text = "One"))

    /** The same fade the overlay would create for itself, held here so its alpha can be read. */
    private val fade = OverlayFade(MESSAGE_FADE_MILLIS)

    private val frameTolerance = 0.06f

    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private fun composeOverlay(firstMessage: String = "One") {
        state.value = MessageOverlayState(text = firstMessage)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AerialTheme {
                MessageOverlay(state = state.value, fade = fade)
            }
        }
        compose.mainClock.advanceTimeBy(16)
    }

    private fun setMessage(text: String) = compose.runOnUiThread { state.value = MessageOverlayState(text = text) }

    /** Applies the new state and starts any tween, without letting it run on. */
    private fun settle() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `with animated changes a new message fades out, swaps while blank and fades back in`() {
        GeneralPrefs.messageAnimateChanges = true
        // Composed blank first, as the screensaver does, so the message is an arrival.
        composeOverlay("")
        settle()
        setMessage("One")
        settle()
        compose.mainClock.advanceTimeBy(MESSAGE_FADE_MILLIS.toLong())

        compose.onNodeWithText("One").assertExists()
        assertEquals(1f, fade.alpha.value, frameTolerance)

        setMessage("Two")
        settle()

        // 150ms out: halfway, still the old message.
        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)
        compose.onNodeWithText("One").assertExists()
        compose.onNodeWithText("Two").assertDoesNotExist()

        // 300ms in: blank, and the new message takes its place at zero alpha.
        compose.mainClock.advanceTimeBy(150)
        assertTrue("the fade-out ran past 300ms", fade.alpha.value < 0.05f)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("One").assertDoesNotExist()
        compose.onNodeWithText("Two").assertExists()
        assertTrue("the swap was not blank", fade.alpha.value < 0.05f)

        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)

        compose.mainClock.advanceTimeBy(150)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }

    @Test
    fun `with animated changes a first message fades in from nothing rather than popping`() {
        GeneralPrefs.messageAnimateChanges = true
        composeOverlay("One")
        settle()

        // The line starts near zero and grows, rather than already being fully opaque.
        assertTrue("the message popped in at full alpha", fade.alpha.value < 0.1f)

        compose.mainClock.advanceTimeBy(150)
        assertTrue("the fade-in stalled", fade.alpha.value > 0.3f)

        compose.mainClock.advanceTimeBy(200)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }

    @Test
    fun `without animated changes a new message swaps at full alpha and clears at once`() {
        GeneralPrefs.messageAnimateChanges = false
        composeOverlay("One")
        settle()

        compose.onNodeWithText("One").assertExists()

        setMessage("Two")
        settle()

        // No fade at all: the new message is there, and the curve was never touched.
        compose.onNodeWithText("One").assertDoesNotExist()
        compose.onNodeWithText("Two").assertExists()
        compose.mainClock.advanceTimeBy(150)
        assertEquals(1f, fade.alpha.value, frameTolerance)

        setMessage("")
        settle()

        compose.onNodeWithText("Two").assertDoesNotExist()
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }
}
