package com.neilturner.aerialviews.ui.helpers

/**
 * Screen edge padding for the overlay corner containers. The layout defaults live in
 * res/values/dimens.xml (screen_border_padding / screen_border_padding_bottom); these
 * settings let the user override them at runtime.
 *
 * Parsing is pure Kotlin because app/build.gradle.kts sets no testOptions, so unit tests
 * cannot touch android framework classes.
 */
object OverlayPaddingHelper {
    const val DEFAULT_HORIZONTAL_DP = 32f
    const val DEFAULT_VERTICAL_DP = 26f
    const val MIN_DP = 0f
    const val MAX_DP = 64f

    fun horizontalDp(value: String?): Float = dpOrDefault(value, DEFAULT_HORIZONTAL_DP)

    fun verticalDp(value: String?): Float = dpOrDefault(value, DEFAULT_VERTICAL_DP)

    fun toPx(
        dp: Float,
        density: Float,
    ): Int = (dp * density).toInt()

    private fun dpOrDefault(
        value: String?,
        default: Float,
    ): Float =
        value
            ?.trim()
            ?.toFloatOrNull()
            ?.takeIf { it.isFinite() }
            ?.coerceIn(MIN_DP, MAX_DP)
            ?: default
}
