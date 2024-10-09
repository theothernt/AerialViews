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
import kotlinx.coroutines.launch
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe

class ProgressBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    private val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var parentWidth: Int = 0
    private var animator: ValueAnimator? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val receiver = EventsReceiver()

    init {
        paint.style = Paint.Style.FILL
        paint.color = COLOR

        coroutineScope.launch {
            //delay(2_000)
            //animateWidth()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        receiver.subscribe<ProgressBarEvent> { event ->
            when (event.state) {
                ProgressState.START -> {
                    animateWidth(event.position, event.duration)
                }
                ProgressState.PAUSE -> {
                    animator?.pause()
                }
                else -> {
                    animator?.cancel()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
        animator?.cancel()
    }

    fun animateWidth(position: Int, length: Int) {
        parentWidth = (this.parent as View).measuredWidth
        val newWidth = calculateWidth(position)

        animator =
            ValueAnimator.ofInt(newWidth, parentWidth).apply {
                duration = length.toLong()
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    layoutParams.width = (animation.animatedValue as Int)
                    requestLayout()
                }
                start()
            }
    }

    private fun calculateWidth(progress: Int): Int {
        val percentage = progress / parentWidth
        return (width - paddingLeft - paddingRight) * percentage + paddingLeft
    }

    override fun onDraw(canvas: Canvas) {
        if (animator == null) {
            return
        }

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        postInvalidate()
    }

    companion object {
        private const val COLOR = 0x66FFFFFF
    }
}

data class ProgressBarEvent(
    val position: Int = 0,
    val duration: Int = 0,
    val state: ProgressState = ProgressState.STOP
)

enum class ProgressState {
    START,
    PAUSE,
    STOP,
}