package com.neilturner.aerialviews.screensaver.compose

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.neilturner.aerialviews.core.R

/**
 * Bundled fonts shared with the legacy renderer.
 *
 * Google Sans has no 300-weight file. The regular face is registered at both 300 and 400 so the
 * default loading/overlay weight (300) does not fall back to a synthetic face or a platform font.
 */
internal object ScreensaverFonts {
    private val googleSans =
        FontFamily(
            Font(R.font.googlesans_regular, FontWeight.Light),
            Font(R.font.googlesans_regular, FontWeight.Normal),
            Font(R.font.googlesans_medium, FontWeight.Medium),
            Font(R.font.googlesans_semibold, FontWeight.SemiBold),
            Font(R.font.googlesans_bold, FontWeight.Bold),
        )

    private val openSans =
        FontFamily(
            Font(R.font.opensans_light, FontWeight.Light),
            Font(R.font.opensans_regular, FontWeight.Normal),
            Font(R.font.opensans_medium, FontWeight.Medium),
            Font(R.font.opensans_semibold, FontWeight.SemiBold),
            Font(R.font.opensans_bold, FontWeight.Bold),
        )

    fun family(typeface: String?): FontFamily =
        when (typeface) {
            "open-sans" -> openSans
            "google-sans" -> googleSans
            else -> FontFamily.Default
        }

    fun weight(value: Int?): FontWeight =
        when (value) {
            300 -> FontWeight.Light
            500 -> FontWeight.Medium
            600 -> FontWeight.SemiBold
            700 -> FontWeight.Bold
            else -> FontWeight.Normal
        }
}
