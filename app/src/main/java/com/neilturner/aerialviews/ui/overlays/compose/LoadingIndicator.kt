package com.neilturner.aerialviews.ui.overlays.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText

private val spinnerSize = 24.dp
private val spinnerGap = 12.dp

/**
 * The startup / changeover indicator: spinner plus status text, in the bottom left corner of the
 * loading layer.
 *
 * Fixed white rather than the overlay colour, matching loading_view.xml, which is shown over the
 * loading background instead of the Aerial footage.
 */
@Composable
fun LoadingIndicator(
    text: String,
    spinnerVisible: Boolean,
    sizeSp: Float,
    weight: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (spinnerVisible) {
            CircularProgressIndicator(
                modifier = Modifier.size(spinnerSize),
                color = Color.White,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(spinnerGap))
        }

        if (text.isNotBlank()) {
            OverlayText(
                text = text,
                sizeSp = sizeSp,
                weight = weight,
                colour = Color.White,
            )
        }
    }
}
