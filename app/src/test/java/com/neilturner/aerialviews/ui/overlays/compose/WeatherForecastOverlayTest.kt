package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.services.weather.ForecastDay
import com.neilturner.aerialviews.services.weather.ForecastEvent
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState
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
 * The forecast column's geometry, measured from the layout it actually produces.
 *
 * The parity work in 9.5 turned on a quirk that is easy to "tidy" back into a bug: the View version
 * declares `elementMargin = 4` and puts it straight on a `bottomMargin`, so the gap under the day
 * name is 4 *raw pixels* at any display density — unlike `cellSpacing`, which it scales. A Compose
 * `4.dp` made the column taller than the View one on the TV. Rendering the same column at 2x and 3x
 * and comparing the gap catches that: as pixels it is 7 both times, as dp it would be 11 and 15.
 *
 * The 3px above the 4px margin is what the text line box leaves below the glyphs; it is constant
 * here because both days use the same size and weight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeatherForecastOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    // The overlay reads its colour, typeface and padding preferences directly, so Kotpref has to
    // be usable. It is normally initialised by its ContentProvider.
    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private val sizeSp = 24f
    private val weight = "400"

    /** `icon = 0` is a day with no icon, which leaves the label and the temperatures adjacent. */
    private fun day(
        name: String,
        high: String,
        low: String,
    ) = ForecastDay(dayName = name, icon = 0, tempHigh = high, tempLow = low)

    @Composable
    private fun forecast(vararg days: ForecastDay) {
        WeatherForecastOverlay(
            state = ForecastOverlayState(event = ForecastEvent(days = days.toList())),
            sizeSp = sizeSp,
            weight = weight,
        )
    }

    private fun show(vararg days: ForecastDay) {
        compose.setContent {
            AerialTheme {
                Column { forecast(*days) }
            }
        }
    }

    /**
     * Pixels between the bottom of the day name and the top of the high temperature. Read from the
     * nodes' bounds in root, which are pixels: the density override is local to each column, so
     * converting Dp back with the rule's density would be wrong by that factor.
     */
    private fun gapUnder(
        name: String,
        high: String,
    ): Float {
        val label = compose.onNodeWithText(name).fetchSemanticsNode().boundsInRoot
        val temperature = compose.onNodeWithText(high).fetchSemanticsNode().boundsInRoot
        return temperature.top - label.bottom
    }

    /**
     * The same column measured on a 2x and a 3x display. A `4.dp` gap would be 8px on the first and
     * 12px on the second, so the two figures cannot both be 7 unless the margin is raw pixels.
     */
    private fun assertGapIsSevenPixels(expectedDensity: Float) {
        assertEquals("the qualifier must reach the composition", expectedDensity, compose.density.density, 0.01f)

        show(day("Mon", "12°", "4°"))
        assertEquals("the gap under the day name", 7f, gapUnder("Mon", "12°"), 0.5f)
    }

    @Test
    @Config(qualifiers = "xhdpi")
    fun `the gap under the day name is seven pixels at 2x`() = assertGapIsSevenPixels(expectedDensity = 2f)

    @Test
    @Config(qualifiers = "xxhdpi")
    fun `the gap under the day name is seven pixels at 3x`() = assertGapIsSevenPixels(expectedDensity = 3f)

    @Test
    fun `the day name sits above the temperatures and the cells run left to right`() {
        show(day("Mon", "12°", "4°"), day("Tue", "14°", "6°"))

        val mon = compose.onNodeWithText("Mon").getUnclippedBoundsInRoot()
        val tue = compose.onNodeWithText("Tue").getUnclippedBoundsInRoot()
        val high = compose.onNodeWithText("12°").getUnclippedBoundsInRoot()
        val low = compose.onNodeWithText("4°").getUnclippedBoundsInRoot()

        assertTrue("the day name sits above the high temperature", mon.bottom <= high.top)
        assertTrue("the high temperature is left of the low one", high.right <= low.left)
        assertTrue("the second day is to the right of the first", tue.left >= mon.right)
        assertTrue(
            "the cells are separated by the 24dp gap, not stacked",
            tue.left - mon.right >= 24.dp,
        )
    }
}
