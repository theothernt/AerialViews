package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.xml.MetadataOverlay
import com.neilturner.aerialviews.ui.screensaver.xml.ScreenController
import com.neilturner.aerialviews.utils.FirebaseHelper
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Who gets told the media is fading out.
 *
 * The View flags its own `MetadataOverlay` views directly. The Compose line has no View to flag, so
 * the controller writes the store instead - but only in Compose mode: the store emission re-renders
 * the View overlays, and their `updateLocationData` would clear the flag it had just been given,
 * breaking the XML engine's own guard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenControllerFadingOutMediaTest {
    private lateinit var context: Context

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

        GeneralPrefs.overlayVisibility = "ALWAYS_VISIBLE"
        GeneralPrefs.mediaFadeInDuration = "0"
        GeneralPrefs.mediaFadeOutDuration = "0"
        GeneralPrefs.slotTopLeft1 = OverlayType.METADATA1
        GeneralPrefs.slotTopRight1 = OverlayType.EMPTY
        GeneralPrefs.slotBottomLeft1 = OverlayType.EMPTY
        GeneralPrefs.slotBottomRight1 = OverlayType.EMPTY
    }

    @After
    fun tearDown() {
        unmockkObject(FirebaseHelper)
    }

    /** `fadeOutCurrentItem` returns early unless an item has reached the screen. */
    private fun ScreenController.startItem() {
        onImagePrepared()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun ScreenController.xmlMetadataOverlays(): List<MetadataOverlay> {
        val found = mutableListOf<MetadataOverlay>()

        fun walk(view: View) {
            if (view is MetadataOverlay) found += view
            if (view is ViewGroup) (0 until view.childCount).forEach { walk(view.getChildAt(it)) }
        }
        walk(view)
        return found
    }

    @Test
    fun `compose mode writes the store flag`() {
        GeneralPrefs.useComposeScreensaver = true
        val controller = ScreenController(context)
        controller.startItem()

        assertFalse("the flag started set", controller.fadingOutMedia)

        controller.onImageFinished()

        assertTrue("the compose metadata line was not told the item is leaving", controller.fadingOutMedia)
    }

    @Test
    fun `xml mode flags its own view and leaves the store alone`() {
        GeneralPrefs.useComposeScreensaver = false
        val controller = ScreenController(context)
        controller.startItem()

        val overlays = controller.xmlMetadataOverlays()
        assertTrue("the fixture built no View metadata overlay", overlays.isNotEmpty())
        assertFalse(overlays.any { it.isFadingOutMedia })

        controller.onImageFinished()

        // The positive control: the same call flagged the View overlay, so a false store flag below
        // means the engine gate held rather than the path never running.
        assertTrue("the View metadata overlay was not flagged", overlays.all { it.isFadingOutMedia })
        assertFalse("the xml engine must not touch the compose store", controller.fadingOutMedia)
    }
}
