package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.SlotPref
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.enums.SlotType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.overlays.state.OverlayUiState
import com.neilturner.aerialviews.ui.theme.AerialTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders the overlay layer headlessly. Robolectric rather than an instrumentation test so these
 * run with the rest of the suite, without a TV or emulator attached.
 */
@RunWith(RobolectricTestRunner::class)
// A plain Application rather than AerialApp: the real one touches prefs and Firebase before the
// test can initialise Kotpref, and none of that is needed to render the overlays
@Config(sdk = [34], application = android.app.Application::class)
class ScreensaverOverlaysTest {
    @get:Rule
    val compose = createComposeRule()

    // The overlays read the size, weight and padding preferences directly, so Kotpref has to be
    // usable. It is normally initialised by its ContentProvider.
    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private fun slot(
        type: SlotType,
        pref: OverlayType,
    ) = SlotPref(pref = pref, type = type, label = type.name)

    private fun setOverlays(
        state: OverlayUiState,
        slots: List<SlotPref>,
        videoPosition: () -> Long = { 0L },
    ) {
        compose.setContent {
            AerialTheme {
                ScreensaverOverlays(
                    state = state,
                    slots = slots,
                    videoPosition = videoPosition,
                    alternate = false,
                    visibleAlpha = 1f,
                    fadeAlpha = 1f,
                    fadeInDurationMillis = 0,
                    fadeOutDurationMillis = 0,
                    fadeCorners = emptySet(),
                )
            }
        }
    }

    @Test
    fun `a message slot renders its text`() {
        setOverlays(
            state = OverlayUiState(message = mapOf(OverlayType.MESSAGE1 to MessageOverlayState(text = "Hello"))),
            slots = listOf(slot(SlotType.SLOT_BOTTOM_LEFT1, OverlayType.MESSAGE1)),
        )

        compose.onNodeWithText("Hello").assertExists()
    }

    @Test
    fun `a message slot with no API message shows the text configured in settings`() {
        GeneralPrefs.messageLine1 = "Station ident"
        setOverlays(
            state = OverlayUiState(),
            slots = listOf(slot(SlotType.SLOT_BOTTOM_LEFT1, OverlayType.MESSAGE1)),
        )

        compose.onNodeWithText("Station ident").assertExists()
    }

    @Test
    fun `an API message replaces the text configured in settings`() {
        GeneralPrefs.messageLine1 = "Station ident"
        setOverlays(
            state = OverlayUiState(message = mapOf(OverlayType.MESSAGE1 to MessageOverlayState(text = "Now boarding"))),
            slots = listOf(slot(SlotType.SLOT_BOTTOM_LEFT1, OverlayType.MESSAGE1)),
        )

        compose.onNodeWithText("Now boarding").assertExists()
        compose.onNodeWithText("Station ident").assertDoesNotExist()
    }

    @Test
    fun `message slots read their own state`() {
        setOverlays(
            state =
                OverlayUiState(
                    message =
                        mapOf(
                            OverlayType.MESSAGE1 to MessageOverlayState(text = "Left message"),
                            OverlayType.MESSAGE2 to MessageOverlayState(text = "Right message"),
                        ),
                ),
            slots =
                listOf(
                    slot(SlotType.SLOT_BOTTOM_LEFT1, OverlayType.MESSAGE1),
                    slot(SlotType.SLOT_BOTTOM_RIGHT1, OverlayType.MESSAGE2),
                ),
        )

        compose.onNodeWithText("Left message").assertExists()
        compose.onNodeWithText("Right message").assertExists()
    }

    @Test
    fun `an empty slot renders nothing`() {
        setOverlays(
            state = OverlayUiState(message = mapOf(OverlayType.MESSAGE1 to MessageOverlayState(text = "Hello"))),
            slots = listOf(slot(SlotType.SLOT_TOP_LEFT1, OverlayType.EMPTY)),
        )

        compose.onNodeWithText("Hello").assertDoesNotExist()
    }

    @Test
    fun `poi text follows the video position`() {
        setOverlays(
            state =
                OverlayUiState(
                    metadata =
                        mapOf(
                            OverlayType.METADATA1 to
                                MetadataOverlayState(
                                    text = "Location",
                                    poi = mapOf(0 to "Start", 30 to "Later"),
                                    metadataType = MetadataType.DYNAMIC,
                                ),
                        ),
                ),
            slots = listOf(slot(SlotType.SLOT_BOTTOM_LEFT1, OverlayType.METADATA1)),
            videoPosition = { 35_000L },
        )

        // The overlay shows the first POI, then the ticker picks up the position one second in
        compose.mainClock.advanceTimeBy(1_100)
        compose.mainClock.advanceTimeBy(500)

        compose.onNodeWithText("Later").assertExists()
    }
}
