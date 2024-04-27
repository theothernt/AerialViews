package com.neilturner.aerialviews.models

import android.view.View

data class OverlayViews(
    val bottomLeftIds: List<View?>,
    val bottomRightIds: List<View?>,
    val topLeftIds: List<View?>,
    val topRightIds: List<View?>
)
