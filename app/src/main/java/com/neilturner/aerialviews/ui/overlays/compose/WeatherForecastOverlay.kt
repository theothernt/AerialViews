package com.neilturner.aerialviews.ui.overlays.compose

import android.util.TypedValue
import android.widget.TextView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.helpers.FontHelper
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayIcon
import com.neilturner.aerialviews.ui.overlays.compose.components.OverlayText
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayColour
import com.neilturner.aerialviews.ui.overlays.compose.components.overlayTextStyle
import com.neilturner.aerialviews.ui.overlays.state.ForecastOverlayState

private val cellSpacing = 24.dp
private val elementMargin = 4.dp

private const val ICON_SCALE = 1.1f
private const val LABEL_SIZE_RATIO = 0.8f
private const val TEMP_SIZE_RATIO = 0.8f

/** Per-element dimming, the same alphas the View version applies on top of the overlay colour. */
private const val LABEL_ALPHA = 200
private const val HIGH_TEMP_ALPHA = 230
private const val LOW_TEMP_ALPHA = 140

/**
 * The View version pads every forecast text by 2dp at the top and -2dp at the bottom, which leaves
 * the box height unchanged while pushing the glyphs down by 2dp. That headroom exists for
 * superscript-like characters such as the degree symbol. Compose rejects negative padding, so the
 * glyph shift is applied as an offset and the line box keeps its natural height.
 */
private val superscriptExtra = 2.dp

/**
 * The View version gives the low temperature 1dp of right padding so its degree symbol is not
 * clipped by the column edge. Padding widens the box without moving the ink, and Compose needs the
 * same else that last pixel is cut off.
 */
private val trailingGlyphExtra = 1.dp

/**
 * The View version's temp separator is a plain `TextView` with no style, so it is a space in the
 * theme's default 14sp sans-serif. Only its width matters: a `Text` composable would also take
 * Material's 24sp line height (it merges `LocalTextStyle`), which made the temperature row 8px
 * taller than the View version. The width is measured once and drawn as a spacer instead.
 */
private val separatorStyle =
    TextStyle(
        fontSize = 14.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = true),
    )

/**
 * Multi-day forecast: a column per day with the day name, icon and high/low temperatures.
 *
 * The number of days comes from the forecast service, which already limits the result to the
 * weatherLine2Days preference.
 */
@Composable
fun WeatherForecastOverlay(
    state: ForecastOverlayState,
    sizeSp: Float,
    weight: String,
) {
    val days = state.event.days
    if (days.isEmpty()) return

    val colour = overlayColour()
    val labelColour = colour.copy(alpha = LABEL_ALPHA / 255f)
    val highColour = colour.copy(alpha = HIGH_TEMP_ALPHA / 255f)
    val lowColour = colour.copy(alpha = LOW_TEMP_ALPHA / 255f)

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val context = LocalContext.current

    // Same calculation as calculateIconSize(size) in the View version: the font's ascent-to-descent
    // span at the full text size, scaled by ICON_SCALE and truncated to whole pixels. A Compose
    // paragraph's height is not that quantity - it is a few px larger, which made every icon ~7px
    // too tall and the whole block 8px taller than the View version.
    val iconSize =
        remember(context, sizeSp, weight, density) {
            val paint =
                TextView(context)
                    .apply {
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                        typeface = FontHelper.getTypeface(context, GeneralPrefs.fontTypeface, weight)
                    }.paint
            val metrics = paint.fontMetrics
            val textHeightPx = metrics.descent - metrics.ascent
            with(density) { (textHeightPx * ICON_SCALE).toInt().toDp() }
        }

    val spaceWidth =
        with(density) {
            measurer
                .measure(AnnotatedString(" "), separatorStyle)
                .size.width
                .toFloat()
                .toDp()
        }

    Row(verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { index, day ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OverlayText(
                    text = day.dayName,
                    sizeSp = sizeSp * LABEL_SIZE_RATIO,
                    weight = weight,
                    modifier =
                        Modifier
                            .padding(bottom = elementMargin)
                            .offset(y = superscriptExtra),
                    colour = labelColour,
                )

                if (day.icon > 0) {
                    OverlayIcon(
                        resId = day.icon,
                        size = iconSize,
                        modifier = Modifier.padding(bottom = elementMargin),
                        colour = colour,
                    )
                }

                Row {
                    OverlayText(
                        text = day.tempHigh,
                        sizeSp = sizeSp * TEMP_SIZE_RATIO,
                        weight = weight,
                        modifier = Modifier.offset(y = superscriptExtra),
                        colour = highColour,
                    )
                    Spacer(Modifier.width(spaceWidth))
                    OverlayText(
                        text = day.tempLow,
                        sizeSp = sizeSp * TEMP_SIZE_RATIO,
                        weight = weight,
                        modifier =
                            Modifier
                                .padding(end = trailingGlyphExtra)
                                .offset(y = superscriptExtra),
                        colour = lowColour,
                    )
                }
            }

            if (index < days.size - 1) {
                Spacer(Modifier.width(cellSpacing))
            }
        }
    }
}
