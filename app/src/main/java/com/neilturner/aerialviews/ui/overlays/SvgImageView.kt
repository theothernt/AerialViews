package com.neilturner.aerialviews.ui.overlays

import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import androidx.core.graphics.withTranslation

class SvgImageView
    @JvmOverloads
    constructor(
        context: android.content.Context,
        attrs: android.util.AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : androidx.appcompat.widget.AppCompatImageView(context, attrs, defStyleAttr) {
        // Shadow properties matching OverlayText style
        private var shadowColor = android.graphics.Color.BLACK
        private var shadowDx = 1f
        private var shadowDy = 1f
        private var shadowRadius = 1f

        // Shadow adjustment factors
        private var shadowDxAdjustment = 0.6f
        private var shadowDyAdjustment = 0.6f
        private var shadowRadiusAdjustment = 1f

        private val shadowPaint = android.graphics.Paint()
        private var originalDrawable: android.graphics.drawable.Drawable? = null

        init {
            shadowColor = android.graphics.Color.BLACK
            shadowDx = 1f
            shadowDy = 1f
            shadowRadius = 1f * shadowRadiusAdjustment

            // Convert density-independent pixels to pixels if needed
            val density = context.resources.displayMetrics.density
            shadowDx *= density
            shadowDy *= density
            shadowRadius *= density

            // Configure shadow paint
            shadowPaint.style = android.graphics.Paint.Style.FILL
            shadowPaint.color = shadowColor

            // Add blur if radius > 0
            if (shadowRadius > 0) {
                shadowPaint.maskFilter = BlurMaskFilter(shadowRadius, Blur.SOLID)
            }

            // Add padding to make room for shadow
            val extraPadding = (shadowRadius * 2).toInt()
            setPadding(extraPadding, extraPadding, extraPadding, extraPadding)
        }

        fun setSvgResource(resId: Int) {
            val drawable =
                androidx.core.content.ContextCompat
                    .getDrawable(context, resId)
            if (drawable != null) {
                originalDrawable = drawable.mutate()
                androidx.core.graphics.drawable.DrawableCompat
                    .setTint(originalDrawable!!, android.graphics.Color.WHITE)
                setImageDrawable(originalDrawable)
            }
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            val drawable = drawable ?: return

            // Calculate shadow offset and extra space needed
            val adjustedDx = shadowDx * shadowDxAdjustment
            val adjustedDy = shadowDy * shadowDyAdjustment
            val shadowBlur = shadowRadius * 2

            // Calculate the content area (excluding padding)
            val contentWidth = width - paddingLeft - paddingRight
            val contentHeight = height - paddingTop - paddingBottom
            
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
                    androidx.core.graphics.drawable.DrawableCompat
                        .setTint(drawable, shadowColor)

                    // Set alpha for shadow (make it slightly transparent)
                    drawable.alpha = android.graphics.Color.alpha(shadowColor)

                    // Draw the shadow
                    drawable.draw(this)
                }

                // Reset for the actual image
                androidx.core.graphics.drawable.DrawableCompat
                    .setTint(drawable, android.graphics.Color.WHITE)
                drawable.alpha = originalAlpha

                // Draw the original white image
                drawable.draw(this)
            }
        }
    }
