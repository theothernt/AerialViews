package com.neilturner.aerialviews.ui.overlays.compose.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One overlay line. Deliberately stateless: the caller owns the text, so ticking and formatting
 * stay outside and this stays previewable.
 */
@Composable
fun OverlayText(
    text: String,
    sizeSp: Float,
    weight: String,
    modifier: Modifier = Modifier,
    colour: Color = overlayColour(),
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    // Clamped to the screen less the horizontal padding, so long text wraps at the padding
    // instead of running under the opposite edge
    val maxWidth = overlayMaxTextWidth()
    val clamp =
        if (maxWidth != null && maxWidth > 0.dp) {
            Modifier.widthIn(max = maxWidth)
        } else {
            Modifier
        }

    Text(
        text = text,
        modifier = modifier.overlayFontOffset(sizeSp).then(clamp),
        style = overlayTextStyle(sizeSp, weight, colour),
        textAlign = textAlign,
        maxLines = maxLines,
    )
}
