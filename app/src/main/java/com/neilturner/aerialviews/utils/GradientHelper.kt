package com.neilturner.aerialviews.utils

import android.graphics.drawable.GradientDrawable

class GradientHelper {
    companion object {
        // Based on https://github.com/larsenwork/postcss-easing-gradients
        // Preview at https://larsenwork.com/easing-gradients/
        fun smoothBackground(orientation: GradientDrawable.Orientation): GradientDrawable {
            val easeOutGradient = intArrayOf(
                0x00000000.toInt(), // 0% → hsla(0, 0%, 0%, 0) → Fully transparent
                0x02000000.toInt(), // 8.1% → hsla(0, 0%, 0%, 0.01)
                0x09000000.toInt(), // 15.5% → hsla(0, 0%, 0%, 0.036)
                0x14000000.toInt(), // 22.5% → hsla(0, 0%, 0%, 0.078)
                0x22000000.toInt(), // 29% → hsla(0, 0%, 0%, 0.132)
                0x31000000.toInt(), // 35.3% → hsla(0, 0%, 0%, 0.194)
                0x43000000.toInt(), // 41.2% → hsla(0, 0%, 0%, 0.264)
                0x56000000.toInt(), // 47.1% → hsla(0, 0%, 0%, 0.338)
                0x69000000.toInt(), // 52.9% → hsla(0, 0%, 0%, 0.412)
                0x7C000000.toInt(), // 58.8% → hsla(0, 0%, 0%, 0.486)
                0x8E000000.toInt(), // 64.7% → hsla(0, 0%, 0%, 0.556)
                0x9E000000.toInt(), // 71% → hsla(0, 0%, 0%, 0.618)
                0xAB000000.toInt(), // 77.5% → hsla(0, 0%, 0%, 0.672)
                0xB6000000.toInt(), // 84.5% → hsla(0, 0%, 0%, 0.714)
                0xBD000000.toInt(), // 91.9% → hsla(0, 0%, 0%, 0.74)
                0xBF000000.toInt()  // 100% → hsla(0, 0%, 0%, 0.75)
            )

            return GradientDrawable(
                orientation,
                easeOutGradient
            )
        }
    }
}