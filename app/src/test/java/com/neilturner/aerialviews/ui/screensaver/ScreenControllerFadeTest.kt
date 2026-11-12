package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.os.Looper
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.OverlayOpacityHelper
import com.neilturner.aerialviews.ui.screensaver.xml.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The Compose overlay layer's fade and the gradient rows that follow it.
 *
 * Until now these were only watched on the TV. The layer itself is a `StateFlow` in the controller
 * feeding `animateFloatAsState`, so what can be pinned headlessly is the value it fades towards and
 * the View gradients that move with it — including the rule that a row holding a persistent overlay
 * keeps its gradient, and that the auto-hide delay is honoured before anything fades.
 *
 * The Compose corner rule (fading corners follow the fade, persistent ones keep the opacity) is
 * tested as a pure function in `OverlayCornersTest`; the tween between the two values is Compose's
 * own `animateFloatAsState`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenControllerFadeTest {
    private lateinit var context: Context

    private val tolerance = 0.001f

    private val visibleAlpha: Float
        get() = OverlayOpacityHelper.visibleAlpha(context)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The controller reads its preferences from Kotpref, which is normally initialised by its
        // ContentProvider. A plain Application is used in @Config so nothing else touches prefs.
        Kotpref.init(context)
        // ScreenController tags the engine it built for crash/analytics attribution.
        mockkObject(FirebaseHelper)
        every { FirebaseHelper.analyticsUserProperty(any(), any(), any()) } returns Unit
        every { FirebaseHelper.crashlyticsLogKeys(any(), any<Boolean>(), any()) } returns Unit

        GeneralPrefs.useComposeScreensaver = true
        GeneralPrefs.overlayVisibility = "HIDE_AFTER_DELAY"
        GeneralPrefs.overlayVisibilityDelay = "0"
        GeneralPrefs.overlayRevealTimeout = "30"
        GeneralPrefs.mediaFadeInDuration = "0"
        GeneralPrefs.overlayFadeInDuration = "0"
        GeneralPrefs.overlayFadeOutDuration = "0"
        GeneralPrefs.showTopGradient = true
        GeneralPrefs.showBottomGradient = true
        // An overlay in the bottom row only. The default fade setting covers all four corners, and
        // it is a read-only pref, so the test does not try to override it: the bottom row fades and
        // the empty top row is left alone either way. Whether a row with a *persistent* overlay
        // keeps its gradient is decided by `shouldFadeRow`, covered in `OverlayCornersTest`.
        GeneralPrefs.slotTopLeft1 = OverlayType.EMPTY
        GeneralPrefs.slotTopRight1 = OverlayType.EMPTY
        GeneralPrefs.slotBottomLeft1 = OverlayType.CLOCK
        GeneralPrefs.slotBottomRight1 = OverlayType.EMPTY
    }

    @After
    fun tearDown() {
        unmockkObject(FirebaseHelper)
    }

    private fun ScreenController.gradientTop(): View = view.findViewById(R.id.gradientTop)

    private fun ScreenController.gradientBottom(): View = view.findViewById(R.id.gradientBottom)

    /** Runs the main looper, optionally far enough ahead for delayed work to fire. */
    private fun idle(millis: Long = 0L) {
        val looper = shadowOf(Looper.getMainLooper())
        if (millis > 0L) looper.idleFor(Duration.ofMillis(millis)) else looper.idle()
    }

    @Test
    fun `hiding the overlays fades the compose layer and only the fading row's gradient`() {
        val controller = ScreenController(context)

        assertEquals(1f, controller.gradientTop().alpha, tolerance)
        assertEquals(1f, controller.gradientBottom().alpha, tolerance)

        // An item reaching the screen is what sets the overlays going in HIDE_AFTER_DELAY mode.
        controller.onImagePrepared()
        idle()

        assertEquals("the compose layer fades towards nothing", 0f, controller.composeFade, tolerance)
        assertEquals("the fading row's gradient goes with it", 0f, controller.gradientBottom().alpha, tolerance)
        assertEquals("a row with nothing to fade keeps its gradient", 1f, controller.gradientTop().alpha, tolerance)
    }

    @Test
    fun `revealing the overlays fades the layer and the gradient back in`() {
        val controller = ScreenController(context)
        controller.onImagePrepared()
        idle()

        // A user reveal, once the item has finished fading in.
        controller.showOverlays()
        idle()

        assertEquals("the compose layer fades back to the overlay opacity", visibleAlpha, controller.composeFade, tolerance)
        assertEquals("the gradient comes back with the overlays", 1f, controller.gradientBottom().alpha, tolerance)
        assertEquals(1f, controller.gradientTop().alpha, tolerance)
    }

    @Test
    fun `always visible keeps the overlays and their gradient up`() {
        GeneralPrefs.overlayVisibility = "ALWAYS_VISIBLE"
        val controller = ScreenController(context)

        controller.onImagePrepared()
        idle()

        assertEquals("the layer stays at the overlay opacity", visibleAlpha, controller.composeFade, tolerance)
        assertEquals("the gradient stays with it", 1f, controller.gradientBottom().alpha, tolerance)
    }

    @Test
    fun `always hidden keeps the overlays down until a reveal`() {
        GeneralPrefs.overlayVisibility = "ALWAYS_HIDDEN"
        val controller = ScreenController(context)

        controller.onImagePrepared()
        idle()

        assertEquals("nothing is shown without a reveal", 0f, controller.composeFade, tolerance)
        assertEquals("the gradient goes down with them", 0f, controller.gradientBottom().alpha, tolerance)

        controller.showOverlays()
        idle()

        assertEquals(visibleAlpha, controller.composeFade, tolerance)
        assertEquals(1f, controller.gradientBottom().alpha, tolerance)
    }

    @Test
    fun `show after delay holds the overlays down until the delay is up`() {
        GeneralPrefs.overlayVisibility = "SHOW_AFTER_DELAY"
        GeneralPrefs.overlayVisibilityDelay = "3"
        val controller = ScreenController(context)

        controller.onImagePrepared()
        idle(2_000)

        assertEquals("nothing shows before the delay", 0f, controller.composeFade, tolerance)
        assertEquals("the gradient waits with the overlays", 0f, controller.gradientBottom().alpha, tolerance)

        idle(1_500)

        assertEquals("the layer appears once the delay is up", visibleAlpha, controller.composeFade, tolerance)
        assertEquals("the gradient comes up with it", 1f, controller.gradientBottom().alpha, tolerance)
    }

    @Test
    fun `the auto-hide delay holds the fade until it has elapsed`() {
        GeneralPrefs.overlayVisibilityDelay = "3"
        val controller = ScreenController(context)

        controller.onImagePrepared()
        idle(2_000)
        assertEquals("nothing fades before the delay is up", visibleAlpha, controller.composeFade, tolerance)

        idle(1_500)
        assertEquals("the fade runs once the delay has elapsed", 0f, controller.composeFade, tolerance)
    }
}
