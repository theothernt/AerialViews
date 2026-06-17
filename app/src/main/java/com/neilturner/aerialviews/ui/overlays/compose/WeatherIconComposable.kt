package com.neilturner.aerialviews.ui.overlays.compose

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

@Composable
fun WeatherIconComposable(
    resourceId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(2, 0, 0, 0)
            }
        },
        update = { imageView ->
            val drawable = ContextCompat.getDrawable(context, resourceId)
            if (drawable != null) {
                val tintedDrawable = drawable.mutate()
                DrawableCompat.setTint(tintedDrawable, Color.WHITE)
                imageView.setImageDrawable(tintedDrawable)
            }
        },
    )
}
