package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.services.weather.WeatherEvent
import com.neilturner.aerialviews.ui.overlays.state.WeatherOverlayState
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
 * The current-weather line's update, checked against Compose's own clock.
 *
 * The View fades out, swaps the content and fades back in over 300ms each way. The Compose port
 * used to swap the content at full brightness, so an update popped instead of fading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class WeatherNowOverlayFadeTest {
    @get:Rule
    val compose = createComposeRule()

    private val state = mutableStateOf(WeatherOverlayState(WeatherEvent(city = "London", temperature = "20°")))

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
                WeatherNowOverlay(
                    state = state.value,
                    layout = "CITY,TEMPERATURE",
                    sizeSp = 18f,
                    weight = "300",
                    fade = fade,
                )
            }
        }
        compose.mainClock.advanceTimeBy(16)
    }

    private fun setWeather(
        city: String,
        temperature: String,
    ) {
        compose.runOnUiThread { state.value = WeatherOverlayState(WeatherEvent(city = city, temperature = temperature)) }
    }

    @Test
    fun `a weather update fades out, swaps and fades back in`() {
        show()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("London").assertExists()
        assertEquals(1f, fade.alpha.value, frameTolerance)

        setWeather("Paris", "21°")
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()

        // 150ms out: halfway, still the old city.
        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)
        compose.onNodeWithText("London").assertExists()
        compose.onNodeWithText("21°").assertDoesNotExist()

        // 300ms in: the line is blank, and the new city takes its place at zero alpha.
        compose.mainClock.advanceTimeBy(150)
        assertTrue("the fade-out ran past 300ms", fade.alpha.value < 0.05f)
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Paris").assertExists()
        compose.onNodeWithText("London").assertDoesNotExist()

        compose.mainClock.advanceTimeBy(150)
        assertEquals(0.5f, fade.alpha.value, 0.1f)

        compose.mainClock.advanceTimeBy(150)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }

    @Test
    fun `a line that was blank snaps to zero before fading in`() {
        state.value = WeatherOverlayState(WeatherEvent())
        show()

        setWeather("Paris", "21°")
        compose.waitForIdle()
        // One frame for the effect to run (snap to zero and swap), one to draw the new text.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        // Nothing was on screen, so there is no 300ms fade-out of an empty line first.
        assertTrue("the blank line faded out first", fade.alpha.value < 0.2f)
        compose.onNodeWithText("Paris").assertExists()

        compose.mainClock.advanceTimeBy(400)
        assertEquals(1f, fade.alpha.value, frameTolerance)
    }
}
