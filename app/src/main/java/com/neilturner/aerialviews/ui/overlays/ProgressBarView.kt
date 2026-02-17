package com.neilturner.aerialviews.ui.overlays

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.neilturner.aerialviews.ui.overlays.state.ProgressOverlayState
import timber.log.Timber

class ProgressBar : View {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    private val paint: Paint = Paint()
    private var parentWidth: Int = 0
    private var animator: ValueAnimator? = null

    init {
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        setBackgroundColor(Color.WHITE)
    }

    fun render(state: ProgressOverlayState) {
        when (state.state) {
            ProgressState.START -> {
                animateWidth(state.position, state.duration)
                Timber.i("Starting progress bar animation: ${state.position / 1000}s, ${state.duration / 1000}s")
            }

            ProgressState.PAUSE -> {
                if (animator?.isRunning == true) {
                    animator?.pause()
                }
                Timber.i("Pausing progress bar animation")
            }

            ProgressState.RESUME -> {
                if (animator?.isPaused == true) {
                    animator?.resume()
                }
                Timber.i("Resuming progress bar animation")
            }

            else -> {
                Timber.i("Reset progress bar animation")
                animator?.cancel()
                layoutParams.width = 0
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    fun animateWidth(
        position: Long,
        length: Long,
    ) {
        parentWidth = (this.parent as View).measuredWidth

        if (parentWidth == 0 || length <= 0L || position < 0L || position > length) {
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
    RESUME,
    RESET,
}
