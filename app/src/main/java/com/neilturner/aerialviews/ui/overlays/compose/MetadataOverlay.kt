package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.overlays.utils.PoiText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POI_POLL_MILLIS = 1000L

// The View waits longer before looking again once the text has just changed, so a POI that lands
// right after another one cannot cut it short
private const val POI_SETTLE_MILLIS = 3000L

// The View fades the line out and back in over a second each way
internal const val POI_FADE_MILLIS = 1000

/**
 * Location / points of interest line.
 *
 * [videoPosition] is read rather than hoisted into state so the poll only exists while a POI
 * overlay is on screen - a screen-wide position ticker would run for every user.
 */
@Composable
fun MetadataOverlay(
    state: MetadataOverlayState,
    sizeSp: Float,
    weight: String,
    videoPosition: () -> Long,
    fadingOutMedia: Boolean,
    // Only shortened by tests: `delay` is not driven by the Compose test clock, so a test cannot
    // reach the first check without waiting a real second for it
    pollMillis: Long = POI_POLL_MILLIS,
    fade: OverlayFade = remember { OverlayFade(POI_FADE_MILLIS) },
) {
    val rotating = state.metadataType == MetadataType.DYNAMIC && state.poi.size > 1
    val initialText =
        remember(state) {
            if (state.metadataType == MetadataType.DYNAMIC) {
                PoiText.at(0, state.poi, state.text)
            } else {
                state.text
            }
        }

    var text by remember(state) { mutableStateOf(initialText) }

    // A new item arrives fully visible, whichever fade instance is in use
    LaunchedEffect(state) { fade.reset() }

    if (rotating) {
        LaunchedEffect(state) {
            // The View's job waits a second before its first check
            delay(pollMillis)
            while (true) {
                val next = PoiText.at(videoPosition() / 1000, state.poi, state.text)
                val shouldUpdate = next != text

                // The View holds the text still while the item fades out, so a POI that falls due in
                // that window is not faded under the loading view; the next item's metadata clears it
                if (shouldUpdate && !fadingOutMedia) {
                    // 1s normally, 3s after a change, as the View's interval does. The View starts its
                    // fade without waiting for it, so the two overlap and the next check lands 3s after
                    // the change rather than 3s after the fade ends.
                    coroutineScope {
                        launch {
                            // Fade the line out to nothing, swap the text while it is invisible, then
                            // fade it back in - a second each way, not a crossfade of the two strings
                            // over each other.
                            fade.fadeOut()
                            text = next
                            fade.fadeIn()
                        }
                        delay(POI_SETTLE_MILLIS)
                    }
                } else {
                    // A pending change still waits the longer interval, as the View's does
                    delay(if (shouldUpdate) POI_SETTLE_MILLIS else pollMillis)
                }
            }
        }
    }

    if (text.isBlank()) return

    OverlayText(
        text = text,
        sizeSp = sizeSp,
        weight = weight,
        modifier = Modifier.alpha(fade.alpha.value),
    )
}
