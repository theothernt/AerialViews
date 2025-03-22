package com.neilturner.aerialviews.ui.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber

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
    private val receiver = EventsReceiver()

    init {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        setBackgroundColor(Color.WHITE)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        receiver.subscribe<ProgressBarEvent> { event ->
            when (event.state) {
                ProgressState.START -> {
                    animateWidth(event.position, event.duration)
                    Timber.i("Starting progress bar animation: ${event.position / 1000}s, ${event.duration / 1000}s")
                }
                ProgressState.PAUSE -> {
                    if (animator?.isRunning == true) {
                        animator?.pause()
                    }
                    Timber.i("Pausing progress bar animation")
                }
                else -> {
                    Timber.i("Reset progress bar animation")
                    animator?.cancel()
                    layoutParams.width = 0
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
        animator?.cancel()
    }

    fun animateWidth(
        position: Long,
        length: Long,
    ) {
        parentWidth = (this.parent as View).measuredWidth

        if (parentWidth == 0 || length <= 0L) {
            return
        }

        val newWidth = (position.toFloat() / length.toFloat()) * parentWidth
        animator =
            ValueAnimator.ofInt(newWidth.toInt(), parentWidth).apply {
                duration = length
                interpolator = LinearInterpolator()
                addUpdateListener { animation ->
                    layoutParams.width = (animation.animatedValue as Int)
                    requestLayout()
                }
                start()
            }
    }
}

data class ProgressBarEvent(
    val state: ProgressState = ProgressState.RESET,
    val position: Long = 0,
    val duration: Long = 0,
)

enum class ProgressState {
    START,
    PAUSE,
    RESET,
}
