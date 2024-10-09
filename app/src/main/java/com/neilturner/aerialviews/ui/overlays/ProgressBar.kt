package com.neilturner.aerialviews.ui.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ProgressBar @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        paint.style = Paint.Style.FILL
        paint.color = COLOR

        coroutineScope.launch {
            delay(1_000)
            //animateWidth()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animateWidth()
    }

    fun animateWidth() {
        val parentWidth = (this.parent as View).measuredWidth
        val widthAnimator = ValueAnimator.ofInt(0, parentWidth)
        widthAnimator.duration = 10_000
        widthAnimator.interpolator = LinearInterpolator()
        widthAnimator.addUpdateListener { animation ->
            layoutParams.width = (animation.animatedValue as Int)
            requestLayout()
        }
        widthAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        postInvalidate()
    }

    companion object {
        private const val COLOR = 0x66FFFFFF
    }
}