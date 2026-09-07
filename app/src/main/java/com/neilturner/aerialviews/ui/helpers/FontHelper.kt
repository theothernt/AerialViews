package com.neilturner.aerialviews.ui.helpers

import android.content.Context
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.TypefaceCompat
import com.neilturner.aerialviews.R
import timber.log.Timber

object FontHelper {
    private const val REFERENCE_FONT = "open-sans"

    fun getTypeface(
        context: Context,
        typeface: String,
        weight: String,
    ): Typeface =
        getTypeface(
            context,
            typeface,
            weight.toInt(),
        )

    fun getTypeface(
        context: Context,
        typeface: String,
        weight: Int,
    ): Typeface {
        val font =
            try {
                if (typeface == "open-sans") {
                    ResourcesCompat.getFont(context, R.font.opensans)
                } else if (typeface == "google-sans") {
                    ResourcesCompat.getFont(context, R.font.googlesans)
                } else {
                    Typeface.create("san-serif", Typeface.NORMAL)
                }
            } catch (ex: Exception) {
                Timber.e(ex)
                Typeface.create("san-serif", Typeface.NORMAL)
            }
        return TypefaceCompat.create(context, font, weight, false)
    }

    /**
     * Computes the vertical baseline offset (in pixels) that should be applied
     * to [typeface] so that its text renders at the same vertical position as
     * the reference font (Open Sans). This compensates for differing font
     * metrics (ascent/descent) between the two bundled fonts.
     *
     * The offset is proportional to [textSizePx], so callers should pass the
     * actual text size of the TextView in pixels.
     */
    fun getFontVerticalOffset(
        context: Context,
        typeface: String,
        textSizePx: Float,
    ): Int {
        if (typeface == REFERENCE_FONT) return 0

        val refPaint =
            TextPaint().apply {
                this.textSize = textSizePx
                this.typeface = getTypeface(context, REFERENCE_FONT, 400)
            }
        val refMetrics = refPaint.fontMetrics

        val targetPaint =
            TextPaint().apply {
                this.textSize = textSizePx
                this.typeface = getTypeface(context, typeface, 400)
            }
        val targetMetrics = targetPaint.fontMetrics

        // The ascent difference determines how far the baseline must shift.
        // refMetrics.ascent - targetMetrics.ascent gives a positive value when
        // the target font extends higher (more negative ascent), meaning the
        // text appears higher and needs to be shifted down (positive shift).
        return (refMetrics.ascent - targetMetrics.ascent).toInt()
    }
}
