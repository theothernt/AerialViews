package com.neilturner.aerialviews.ui.screensaver

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.LoadingStatus
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.screensaver.xml.ScreenController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the loading indicator across both engines.
 *
 * The regression this guards against: the media status callback used to re-show the XML spinner
 * after the Compose layer had hidden it, so the screensaver drew two spinners side by side. The
 * XML spinner and label now stay hidden whenever the Compose indicator is installed, and the
 * XML mode assertions make sure that guard did not simply disable the indicator everywhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ScreenControllerLoadingTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // The controller reads its preferences from Kotpref, which is normally initialised by its
        // ContentProvider. A plain Application is used in @Config so nothing else touches prefs.
        Kotpref.init(context)
        GeneralPrefs.showLoadingText = true
    }

    private fun controller(composeEngine: Boolean): ScreenController {
        GeneralPrefs.useComposeScreensaver = composeEngine
        return ScreenController(context)
    }

    private fun View.selfAndDescendants(): Sequence<View> =
        sequence {
            yield(this@selfAndDescendants)
            if (this@selfAndDescendants is ViewGroup) {
                for (index in 0 until childCount) {
                    yieldAll(getChildAt(index).selfAndDescendants())
                }
            }
        }

    private fun ScreenController.loadingContainer(): View = view.findViewById(R.id.loading_container)

    private fun ScreenController.xmlSpinner(): View = view.findViewById(R.id.loading_spinner)

    private fun ScreenController.xmlLabel(): View = view.findViewById(R.id.loading_text)

    /** Every View-drawn spinner still visible inside the loading layer. */
    private fun ScreenController.visibleSpinners(): List<View> =
        loadingContainer()
            .selfAndDescendants()
            .filterIsInstance<ProgressBar>()
            .filter { it.isVisible }
            .toList()

    private fun ScreenController.composeIndicators(): List<View> =
        loadingContainer().selfAndDescendants().filterIsInstance<ComposeView>().toList()

    @Test
    fun `a media status leaves the XML spinner and label hidden in compose mode`() {
        val controller = controller(composeEngine = true)

        assertEquals(View.GONE, controller.xmlSpinner().visibility)
        assertEquals(View.GONE, controller.xmlLabel().visibility)

        controller.onLoadingStatus(LoadingStatus.LOADING)

        assertEquals(View.GONE, controller.xmlSpinner().visibility)
        assertEquals(View.GONE, controller.xmlLabel().visibility)
        assertTrue(controller.composeLoadingIndicatorVisible)
        assertEquals(
            "the compose indicator must be the only visible indicator",
            0,
            controller.visibleSpinners().size,
        )
        assertEquals(
            "exactly one compose indicator must be installed",
            1,
            controller.composeIndicators().size,
        )
    }

    @Test
    fun `the XML spinner is still driven by media statuses in xml mode`() {
        val controller = controller(composeEngine = false)

        controller.onLoadingStatus(LoadingStatus.LOADING)

        assertEquals(View.VISIBLE, controller.xmlSpinner().visibility)
        assertEquals(context.getString(R.string.loading_title), (controller.xmlLabel() as android.widget.TextView).text)
        assertEquals(
            "the XML spinner must be the only visible indicator",
            1,
            controller.visibleSpinners().size,
        )
        assertEquals(
            "no compose indicator may be installed in xml mode",
            0,
            controller.composeIndicators().size,
        )
    }
}
