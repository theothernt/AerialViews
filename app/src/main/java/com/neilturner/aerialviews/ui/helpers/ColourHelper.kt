package com.neilturner.aerialviews.ui.helpers

import android.graphics.Color
import androidx.core.graphics.toColorInt

object ColourHelper {
    fun colourFromString(colourString: String): Int =
        try {
            colourString.toColorInt()
        } catch (e: IllegalArgumentException) {
            Color.BLACK // Default if parsing fails
        }
}
