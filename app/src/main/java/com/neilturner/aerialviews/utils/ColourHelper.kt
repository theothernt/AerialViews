package com.neilturner.aerialviews.utils

import android.graphics.Color
import androidx.core.graphics.toColorInt

object ColourHelper {
    fun colourFromString(colourString: String): Int =
        try {
            colourString.toColorInt()
        } catch (e: IllegalArgumentException) {
            Color.BLACK // Default if parsing fails
        }
    
    fun isBlurredBackground(colourString: String): Boolean {
        return colourString == "BLURRED"
    }
}
