package com.neilturner.aerialviews.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View

class GradientOverlay {
    companion object {
        fun apply(view: View) {
            // Wait for view to be laid out to get correct height
            view.post {
                val paint = Paint()
                paint.shader = createEaseOutGradient(view.height.toFloat())

                // Create a drawable that uses our gradient paint
                val gradientDrawable = object : Drawable() {
                    override fun draw(canvas: Canvas) {
                        canvas.drawRect(bounds, paint)
                    }

                    override fun setAlpha(alpha: Int) {}
                    override fun setColorFilter(colorFilter: ColorFilter?) {}
                    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
                }

                // Add our gradient as an overlay
                view.overlay.add(gradientDrawable)

                // Set the drawable bounds to match the view
                gradientDrawable.bounds = Rect(0, 0, view.width, view.height)
            }
        }

        private fun createEaseOutGradient(height: Float): Shader {
            return LinearGradient(
                0f,
                0f,
                0f,
                height,
                intArrayOf(
                    Color.argb(2, 0, 0, 0),      // 99.9% transparent
                    Color.argb(51, 0, 0, 0),     // 20% opacity (#33)
                    Color.argb(102, 0, 0, 0),    // 40% opacity (#66)
                    Color.argb(153, 0, 0, 0),    // 60% opacity (#99)
                    Color.argb(179, 0, 0, 0),    // 70% opacity (#B3)
                    Color.argb(204, 0, 0, 0)     // 80% opacity (#CC)
                ),
                floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }
}