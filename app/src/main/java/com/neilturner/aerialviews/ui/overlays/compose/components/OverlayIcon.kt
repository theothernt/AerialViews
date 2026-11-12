package com.neilturner.aerialviews.ui.overlays.compose.components

import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import com.neilturner.aerialviews.ui.overlays.SvgImageView

/**
 * Weather icons via the existing SvgImageView, which already draws the shadow and applies the
 * tint. Interop rather than a rewrite: the icons are vector drawables, and reimplementing the
 * shadow pass in Compose would change how they look.
 *
 * The View is cached per slot by AndroidView, so the drawable is only decoded once.
 */
@Composable
fun OverlayIcon(
    @DrawableRes resId: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    colour: Color = overlayColour(),
) {
    AndroidView(
        modifier = modifier.size(size),
        factory = { context ->
            SvgImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        },
        update = { view ->
            view.setSvgResource(resId)
            view.setTintColour(colour.toArgb())
        },
    )
}
