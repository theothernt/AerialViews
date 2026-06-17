package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.neilturner.aerialviews.models.enums.MetadataType
import com.neilturner.aerialviews.ui.overlays.state.MetadataOverlayState
import kotlinx.coroutines.delay

@Composable
fun MetadataOverlayComposable(
    state: MetadataOverlayState,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    var displayText by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }

    val poi = state.poi
    val metadataType = state.metadataType

    LaunchedEffect(state) {
        if (metadataType == MetadataType.DYNAMIC && poi.isNotEmpty()) {
            val initialText = poi[0]?.replace("\n", " ") ?: state.text
            displayText = initialText
            isVisible = initialText.isNotBlank()
        } else {
            displayText = state.text
            isVisible = state.text.isNotBlank()
        }
    }

    LaunchedEffect(poi, metadataType) {
        if (metadataType != MetadataType.DYNAMIC || poi.size <= 1) return@LaunchedEffect

        val poiTimes = poi.keys.sorted()
        var lastPoi = 0

        while (true) {
            delay(1000)
            val timeSeconds = currentPositionMs / 1000
            val newPoi = poiTimes.findLast { it <= timeSeconds } ?: 0

            if (newPoi != lastPoi) {
                lastPoi = newPoi
                val nextText = poi[newPoi]?.replace("\n", " ") ?: ""
                displayText = nextText
                isVisible = nextText.isNotBlank()
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Text(
            text = displayText,
            style = OverlayTextStyle,
        )
    }
}
