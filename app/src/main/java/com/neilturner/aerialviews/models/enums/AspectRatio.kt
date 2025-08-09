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

        /**
         * Determines aspect ratio from a ratio value (width/height)
         */
        fun fromRatio(
            ratio: Float,
            tolerance: Float = 0.05f,
        ): AspectRatio =
            when {
                ratio > 1 + tolerance -> LANDSCAPE
                ratio < 1 - tolerance -> PORTRAIT
                else -> SQUARE
            }
    }

    /**
     * Returns true if this is a horizontal orientation (landscape or square)
     */
    fun isHorizontal(): Boolean = this == LANDSCAPE || this == SQUARE

    /**
     * Returns true if this is a vertical orientation (portrait)
     */
    fun isVertical(): Boolean = this == PORTRAIT

    /**
     * Returns the aspect ratio as a human-readable string
     */
    fun toDisplayString(): String =
        when (this) {
            PORTRAIT -> "Portrait"
            LANDSCAPE -> "Landscape"
            SQUARE -> "Square"
        }
}
