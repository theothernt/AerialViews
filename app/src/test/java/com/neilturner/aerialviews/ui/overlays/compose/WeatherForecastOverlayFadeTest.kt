package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * The multi-day forecast line's update, checked against Compose's own clock.
 *
 * The View fades out, swaps the forecast and fades back in over 300ms each way; the Compose port
 * used to swap the content at full brightness, so a refresh popped instead of fading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeatherForecastOverlayFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private val monday = ForecastDay(dayName = "Mon", tempHigh = "20°", tempLow = "10°")
    private val tuesday = ForecastDay(dayName = "Tue", tempHigh = "21°", tempLow = "11°")

    private val state = mutableStateOf(ForecastOverlayState(ForecastEvent(days = listOf(monday))))

    private val fade = OverlayFade(WEATHER_FADE_MILLIS)

    private val frameTolerance = 0.06f

    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            AerialTheme {
                WeatherForecastOverlay(
                    state = state.value,
                    sizeSp = 18f,
                    weight = "300",
                    fade = fade,
                )
            }
        }
        compose.mainClock.advanceTimeBy(16)
    }

    private fun setForecast(day: ForecastDay) {
        compose.runOnUiThread { state.value = ForecastOverlayState(ForecastEvent(days = listOf(day))) }
    }

    @Test
    fun `a forecast update fades out, swaps and fades back in`() {
        show()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Mon").assertExists()
        assertEquals(1f, fade.alpha.value, frameTolerance)

        setForecast(tuesday)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()

        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)
        compose.onNodeWithText("Mon").assertExists()
        compose.onNodeWithText("Tue").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(150)
        assertTrue("the fade-out ran past 300ms", fade.alpha.value < 0.05f)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Tue").assertExists()
        compose.onNodeWithText("Mon").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)

        compose.mainClock.advanceTimeBy(150)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }

    @Test
    fun `a blank forecast snaps hidden rather than fading out an empty line`() {
        state.value = ForecastOverlayState(ForecastEvent())
        show()

        setForecast(monday)
        compose.waitForIdle()
        // One frame for the effect to run (snap to zero and swap), one to draw the new text.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertTrue("the blank forecast faded out first", fade.alpha.value < 0.2f)
        compose.onNodeWithText("Mon").assertExists()

        compose.mainClock.advanceTimeBy(400)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }
}
