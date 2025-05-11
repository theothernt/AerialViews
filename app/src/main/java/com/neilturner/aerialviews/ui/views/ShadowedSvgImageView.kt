package com.neilturner.aerialviews.ui.views

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.withTranslation
import com.neilturner.aerialviews.R
import timber.log.Timber

class ShadowedSvgImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // Shadow properties matching OverlayText style
    private var shadowColor: Int = Color.BLACK
    private var shadowDx: Float = 1f
    private var shadowDy: Float = 1f
    private var shadowRadius: Float = 1f
    private var shadowAlpha: Int = 180 // Default shadow alpha (0-255)
    
    // Shadow adjustment factors
    private var shadowDxAdjustment: Float = 0.5f
    private var shadowDyAdjustment: Float = 0.5f
    private var shadowRadiusAdjustment: Float = 0.5f
    
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var originalDrawable: Drawable? = null
    
    init {
        // Extract shadow properties from the OverlayText style
        val typedArray = context.obtainStyledAttributes(R.style.OverlayText, intArrayOf(
            android.R.attr.shadowColor,
            android.R.attr.shadowDx,
            android.R.attr.shadowDy,
            android.R.attr.shadowRadius
        ))
        
        try {
            shadowColor = typedArray.getColor(0, Color.BLACK)
            shadowDx = typedArray.getFloat(1, 1f)
            shadowDy = typedArray.getFloat(2, 1f)
            shadowRadius = typedArray.getFloat(3, 1f)
            
            // Convert density-independent pixels to pixels if needed
            val density = context.resources.displayMetrics.density
            shadowDx *= density
            shadowDy *= density
            shadowRadius *= density
            
            Timber.d("Shadow properties: color=$shadowColor, dx=$shadowDx, dy=$shadowDy, radius=$shadowRadius")
        } finally {
            typedArray.recycle()
        }

        // Configure shadow paint
        shadowPaint.style = Paint.Style.FILL
        shadowPaint.color = shadowColor
        
        // Add blur if radius > 0
        if (shadowRadius > 0) {
            shadowPaint.maskFilter = BlurMaskFilter(shadowRadius, BlurMaskFilter.Blur.NORMAL)
        }
    }
    
    /**
     * Sets an SVG resource with white tint
     */
    fun setSvgResource(resId: Int) {
        val drawable = ContextCompat.getDrawable(context, resId)
        if (drawable != null) {
            originalDrawable = drawable.mutate()
            DrawableCompat.setTint(originalDrawable!!, Color.WHITE)
            setImageDrawable(originalDrawable)
        }
    }
    
    /**
     * Adjust shadow properties with multiplier factors to fine-tune the shadow appearance
     * @param dxFactor Multiplier for shadow x-offset (default: 1.0)
     * @param dyFactor Multiplier for shadow y-offset (default: 1.0)
     * @param radiusFactor Multiplier for shadow blur radius (default: 1.0)
     */
    fun adjustShadow(dxFactor: Float = 1.0f, dyFactor: Float = 1.0f, radiusFactor: Float = 1.0f) {
        shadowDxAdjustment = dxFactor
        shadowDyAdjustment = dyFactor
        shadowRadiusAdjustment = radiusFactor
        
        // Update blur if radius changes
        if (shadowRadius * shadowRadiusAdjustment > 0) {
            shadowPaint.maskFilter = BlurMaskFilter(shadowRadius * shadowRadiusAdjustment, BlurMaskFilter.Blur.NORMAL)
        }
        
        Timber.d("Adjusted shadow: dx=${shadowDx * shadowDxAdjustment}, dy=${shadowDy * shadowDyAdjustment}, radius=${shadowRadius * shadowRadiusAdjustment}")
        
        // Request redraw to apply changes
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        val drawable = drawable ?: return
        
        // Ensure the drawable uses the full bounds of the view
        drawable.setBounds(0, 0, width, height)
        
        // Save original alpha
        val originalAlpha = drawable.alpha
        
        // Apply adjustments to shadow properties
        val adjustedDx = shadowDx * shadowDxAdjustment
        val adjustedDy = shadowDy * shadowDyAdjustment
        
        // Draw shadow first
        canvas.withTranslation(adjustedDx, adjustedDy) {
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
        drawable.draw(canvas)
    }
}
