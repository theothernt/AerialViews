package com.neilturner.aerialviews.ui.overlays

import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Paint.Style
import android.graphics.drawable.Drawable
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.withTranslation

class SvgImageView
    @JvmOverloads
    constructor(
        context: android.content.Context,
        attrs: android.util.AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : AppCompatImageView(context, attrs, defStyleAttr) {
        private var shadowColor = Color.argb(120, 0, 0, 0)
        private var shadowDx = 1f
        private var shadowDy = 1f
        private var shadowRadius = 1f // Increasing this makes the icon smaller

        private val shadowPaint = Paint()
        private var originalDrawable: Drawable? = null

        init {
            // Convert density-independent pixels to pixels if needed
            val density = context.resources.displayMetrics.density
            shadowDx *= density
            shadowDy *= density
            shadowRadius *= density

            // Configure shadow paint
            shadowPaint.style = Style.FILL
            shadowPaint.color = shadowColor
            shadowPaint.maskFilter = BlurMaskFilter(shadowRadius, Blur.NORMAL)

            setPadding(3,0,0,0)
        }

        fun setSvgResource(resId: Int) {
            val drawable = ContextCompat.getDrawable(context, resId)
            if (drawable != null) {
                originalDrawable = drawable.mutate()
                DrawableCompat.setTint(originalDrawable!!, Color.WHITE)
                setImageDrawable(originalDrawable)
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val drawable = drawable ?: return

            // Calculate shadow offset and extra space needed
            val adjustedDx = shadowDx
            val adjustedDy = shadowDy
            val shadowBlur = shadowRadius * 2

            // Calculate the content area (excluding padding)
            val contentWidth = width
            val contentHeight = height

            // Calculate drawable size to fit in content area
            val drawableWidth = contentWidth - shadowBlur.toInt()
            val drawableHeight = contentHeight - shadowBlur.toInt()

            // Set bounds to the reduced size to accommodate shadow
            drawable.setBounds(0, 0, drawableWidth, drawableHeight)

            // Save canvas state
            canvas.withTranslation(paddingLeft.toFloat(), paddingTop.toFloat()) {
                // Translate to respect padding and center the drawable
                // Save original alpha
                val originalAlpha = drawable.alpha

                // Draw shadow first
                withTranslation(adjustedDx, adjustedDy) {
                    // For the shadow, we'll use the shadowColor with proper alpha
                    DrawableCompat.setTint(drawable, shadowColor)

                    // Set alpha for shadow (make it slightly transparent)
                    drawable.alpha = Color.alpha(shadowColor)

                    // Draw the shadow
                    drawable.draw(this)
                }

                // Reset for the actual image
                DrawableCompat.setTint(drawable, Color.WHITE)
                drawable.alpha = originalAlpha

                // Draw the original white image
                drawable.draw(this)
            }
        }
    }
