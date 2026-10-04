package com.neilturner.aerialviews.ui.helpers

import android.content.Context
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

/**
 * Overlays fade between 0f (hidden) and a "visible" alpha that the user can reduce
 * via the Overlay opacity setting, so every fade-in target must use the configured
 * value instead of a hardcoded 1f.
 */
object OverlayOpacityHelper {
    // Must stay within the range offered by R.array.overlay_opacity_values (10..100)
    const val MIN_PERCENT = 10
    const val MAX_PERCENT = 100
    const val FADE_THRESHOLD = 0.95f

    fun alphaFromPercent(value: String?): Float {
        val percent = value?.toIntOrNull() ?: MAX_PERCENT
        return percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / 100f
    }

    fun visibleAlpha(context: Context): Float = alphaFromPercent(GeneralPrefs.overlayOpacity)

    // Is the view currently showing, rather than faded out by auto-hide/reveal?
    fun isVisible(
        alpha: Float,
        visibleAlpha: Float,
    ): Boolean = alpha >= visibleAlpha * FADE_THRESHOLD
}
