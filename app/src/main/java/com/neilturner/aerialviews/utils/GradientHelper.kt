package com.neilturner.aerialviews.utils

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import kotlin.math.pow

object GradientHelper {
    // Based on https://github.com/larsenwork/postcss-easing-gradients
    // Preview at https://larsenwork.com/easing-gradients/
    // Adapted from https://stackoverflow.com/a/73504398/247257
    fun smoothBackgroundAlt(orientation: GradientDrawable.Orientation): GradientDrawable {
        val equation: (Double) -> Double = { x ->
            if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3.0) / 2
        }

        val colors = mutableListOf<Int>()
        val min = 0.3
        val max = 1.0
        val steps = 20.0

        var i = min
        while (i <= max) {
            val alpha = (1 - equation(i)).toFloat()
            val color = Color.argb((alpha * 255).toInt(), 0, 0, 0) // Black with calculated alpha
            colors.add(color)
            i += max / steps
        }

        val gradientDrawable =
            GradientDrawable(
                orientation,
                colors.toIntArray(),
            )

        gradientDrawable.gradientType = GradientDrawable.LINEAR_GRADIENT
        return gradientDrawable
    }
}
