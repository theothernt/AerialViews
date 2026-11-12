package com.neilturner.aerialviews.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * The screensaver has no Material surfaces, but wrapping in MaterialTheme gives Text and
 * CircularProgressIndicator a dark default scheme instead of the light one. Keep it minimal.
 */
@Composable
fun AerialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        typography = AerialTypography,
        content = content,
    )
}
