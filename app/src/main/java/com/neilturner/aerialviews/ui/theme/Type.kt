package com.neilturner.aerialviews.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * Defaults mirror res/values/styles.xml (OverlayText / LoadingText): 18sp, thin sans-serif,
 * white, 1px shadow.
 *
 * Overlay composables do not read these directly - they build their style from the per-overlay
 * preferences via overlayTextStyle(), because every overlay has its own size and weight.
 */
val AerialTypography =
    Typography(
        bodyMedium = TextStyle(fontSize = 18.sp),
    )
