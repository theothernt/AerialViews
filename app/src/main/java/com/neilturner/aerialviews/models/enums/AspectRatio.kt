package com.neilturner.aerialviews.models.enums

enum class AspectRatio {
    PORTRAIT,
    LANDSCAPE,
    SQUARE,
    ;

    companion object {
        /**
         * Determines aspect ratio from width and height dimensions
         */
        fun fromDimensions(
            width: Int,
            height: Int,
        ): AspectRatio =
            when {
                width > height -> LANDSCAPE
                height > width -> PORTRAIT
                else -> SQUARE
            }
    }
}
