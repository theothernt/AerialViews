package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class ProgressBar @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null) :
    View(context, attrs) {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress: Float = 0F

    init {
        paint.style = Paint.Style.FILL
        paint.setColor(COLOR)

        coroutineScope.launch {
            1.until(100).forEach { index ->
                delay(1000)
                progress = index / 100f
                //postInvalidate()
            }
        }
    }

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        Timber.i("onDraw - $progress")
        val x = progress * width
        canvas.drawRect(0f, 0f, x, height.toFloat(), paint)
        postInvalidate()
    }

    companion object {
        private const val COLOR = 0x66FFFFFF
    }
}