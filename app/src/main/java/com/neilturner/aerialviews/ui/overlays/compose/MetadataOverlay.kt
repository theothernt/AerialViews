package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import com.neilturner.aerialviews.ui.overlays.utils.PoiText
import kotlinx.coroutines.delay

private const val POI_POLL_MILLIS = 1000L
private const val POI_FADE_MILLIS = 300

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

    if (rotating) {
        LaunchedEffect(state) {
            while (true) {
                delay(POI_POLL_MILLIS)
                text = PoiText.at(videoPosition() / 1000, state.poi, state.text)
            }
        }
    }

    if (text.isBlank()) return

    Crossfade(
        targetState = text,
        animationSpec = tween(durationMillis = POI_FADE_MILLIS),
        label = "poiText",
    ) { value ->
        OverlayText(
            text = value,
            sizeSp = sizeSp,
            weight = weight,
        )
    }
}
