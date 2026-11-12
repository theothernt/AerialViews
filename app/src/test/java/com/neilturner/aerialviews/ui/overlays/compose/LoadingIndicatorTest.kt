package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chibatching.kotpref.Kotpref
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.theme.AerialTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Compose half of the loading indicator. The screen controller test proves the XML spinner
 * stays hidden beside it; this proves the indicator itself draws a single spinner and a single
 * label, so a second one cannot come from the component either.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LoadingIndicatorTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        Kotpref.init(ApplicationProvider.getApplicationContext())
    }

    private fun indicator(spinnerVisible: Boolean) {
        compose.setContent {
            AerialTheme {
                LoadingIndicator(
                    text = "Loading...",
                    spinnerVisible = spinnerVisible,
                    sizeSp = GeneralPrefs.loadingTextSize.toFloat(),
                    weight = GeneralPrefs.loadingTextWeight,
                )
            }
        }
    }

    private fun spinners() =
        compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true,
        )

    @Test
    fun `a loading state renders one spinner and one label`() {
        indicator(spinnerVisible = true)

        spinners().assertCountEquals(1)
        compose.onAllNodesWithText("Loading...").assertCountEquals(1)
    }

    @Test
    fun `an error state renders the label without a spinner`() {
        indicator(spinnerVisible = false)

        spinners().assertCountEquals(0)
        compose.onNodeWithText("Loading...").assertExists()
    }
}
