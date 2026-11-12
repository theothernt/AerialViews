package com.neilturner.aerialviews.ui.overlays.compose.components

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.ColourHelper
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.helpers.OverlayPaddingHelper
import com.neilturner.aerialviews.ui.theme.OverlayShadow

/** Text and icon colour from the OverlayColour preference. */
@Composable
fun overlayColour(): Color =
    remember(GeneralPrefs.overlayColour) {
        Color(ColourHelper.overlayColour(GeneralPrefs.overlayColour))
    }

/**
 * The Compose equivalent of OverlayHelper.applyFontToTextView() and the OverlayText style:
 * white text at the requested size, 1px black shadow, no font padding.
 *
 * The Typeface from FontHelper already has the requested weight applied, so this does not also
 * set a FontWeight - doing both would let Compose synthesise a different face.
 */
@Composable
fun overlayTextStyle(
    sizeSp: Float,
    weight: String,
    colour: Color = overlayColour(),
): TextStyle {
    val context = LocalContext.current
    val typeface =
        remember(context, weight) {
            FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
        }

    return remember(typeface, sizeSp, colour) {
        TextStyle(
            color = colour,
            fontSize = sizeSp.sp,
            fontFamily = FontFamily(typeface),
            letterSpacing = 0.sp,
            shadow = Shadow(color = OverlayShadow, offset = Offset(1f, 1f), blurRadius = 1f),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
}

/**
 * Font-metric compensation: the Compose equivalent of FontHelper.getFontVerticalOffset() and the
 * setPadding(0, +offset, 0, -offset) the XML overlays apply.
 *
 * Modifier.padding rejects negative values, so the shift is applied as an offset instead. The
 * text lands at the same position, but unlike the View version the line box is not shortened by
 * the offset, so a stack of overlays can end up a couple of px taller. Worth comparing against
 * the XML screenshots before the Compose layer becomes the default.
 */
@Composable
fun fontMetricOffset(sizeSp: Float): Dp {
    val context = LocalContext.current
    val density = LocalDensity.current

    return remember(context, density, sizeSp) {
        val sizePx = with(density) { sizeSp.sp.toPx() }
        val offsetPx = FontHelper.getFontVerticalOffset(context, GeneralPrefs.fontTypeface, sizePx)
        with(density) { offsetPx.toDp() }
    }
}

/** Applies the font-metric compensation for text of [sizeSp]. */
@Composable
fun Modifier.overlayFontOffset(sizeSp: Float): Modifier = offset(y = fontMetricOffset(sizeSp))

/**
 * The widest an overlay line may be: the screen less the user's horizontal padding on both sides.
 *
 * This is the Compose counterpart of the TextView.maxWidth the View layer applies to the now
 * playing overlay. Without it a long point of interest runs under the opposite edge instead of
 * wrapping at the padding.
 *
 * Null when the layer has not been laid out yet, so callers do not clamp to zero.
 */
@Composable
fun overlayMaxTextWidth(): Dp? {
    val density = LocalDensity.current
    val containerWidthPx = LocalWindowInfo.current.containerSize.width
    val horizontalPadding = OverlayPaddingHelper.horizontalDp(GeneralPrefs.overlayPaddingHorizontal)

    return remember(density, containerWidthPx, horizontalPadding) {
        if (containerWidthPx <= 0) {
            null
        } else {
            with(density) { containerWidthPx.toDp() } - horizontalPadding.dp * 2
        }
    }
}
