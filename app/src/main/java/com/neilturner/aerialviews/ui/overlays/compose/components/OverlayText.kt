package com.neilturner.aerialviews.ui.overlays.compose.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

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
    Text(
        text = text,
        modifier = modifier.overlayFontOffset(sizeSp),
        style = overlayTextStyle(sizeSp, weight, colour),
        textAlign = textAlign,
        maxLines = maxLines,
    )
}
