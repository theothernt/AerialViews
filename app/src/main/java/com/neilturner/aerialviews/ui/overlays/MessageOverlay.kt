package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.utils.FontHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class MessageOverlay : AppCompatTextView {
    var type = OverlayType.EMPTY

    private var currentMessage = MessageOverlayState()
    private val prefs = GeneralPrefs
    private var shouldUpdate = false
    private var isUpdating = false
    private val minVisibleAlphaForFade = 0.95f
    private var scopeJob = SupervisorJob()
    private var mainScope = CoroutineScope(Dispatchers.Main + scopeJob)
    private var clearJob: Job? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (scopeJob.isCancelled) {
            scopeJob = SupervisorJob()
            mainScope = CoroutineScope(Dispatchers.Main + scopeJob)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearJob?.cancel()
        scopeJob.cancel()
    }

    fun message(message: String) {
        text = message
        alpha = 1f
        visibility = if (message.isBlank()) GONE else VISIBLE
    }

    fun render(state: MessageOverlayState) {
        if (currentMessage != state) {
            currentMessage = state
            requestUpdate()
        }
    }

    private fun requestUpdate() {
        clearJob?.cancel()
        clearJob = null

        if (!isUpdating) {
            mainScope.launch { updateMessage() }
        } else {
            shouldUpdate = true
        }
    }

    private suspend fun updateMessage() {
        isUpdating = true
        clearJob?.cancel()
        clearJob = null
        shouldUpdate = false

        if (!prefs.messageAnimateChanges) {
            animate().cancel()
            clearAnimation()
            updateTextAndStyle()
            applyVisibilityImmediate()
            scheduleAutoClear()
            isUpdating = false
            if (shouldUpdate) {
                updateMessage()
            }
            return
        }

        if (isGone) {
            alpha = 0f
        } else if (alpha >= minVisibleAlphaForFade) {
            fadeOut()
        }

        updateTextAndStyle()

        if (!text.isNullOrBlank() && alpha < minVisibleAlphaForFade) {
            fadeIn()
        }

        animateOverlays()
        scheduleAutoClear()

        isUpdating = false
        if (shouldUpdate) {
            updateMessage()
        }
    }

    private fun scheduleAutoClear() {
        val durationSeconds = currentMessage.duration
        if (!text.isNullOrBlank() && durationSeconds != null && durationSeconds > 0) {
            clearJob =
                mainScope.launch {
                    delay(durationSeconds * 1000L)
                    if (!text.isNullOrBlank()) {
                        Timber.i("$type: Auto-clearing message after $durationSeconds seconds")
                        clearJob = null
                        currentMessage = currentMessage.copy(text = "", duration = 0)
                        requestUpdate()
                    }
                }
        }
    }

    private fun animateOverlays() {
        val layout = parent as? ConstraintLayout ?: return

        TransitionManager.beginDelayedTransition(
            layout,
            TransitionSet().apply {
                ordering = TransitionSet.ORDERING_TOGETHER
                addTransition(Fade())
                addTransition(ChangeBounds())
                duration = 300
            },
        )

        if (!isGone && text.isNullOrBlank()) {
            Timber.i("$type: Transition... GONE")
            visibility = GONE
        } else if (isGone && !text.isNullOrBlank()) {
            Timber.i("$type: Transition... VISIBLE")
            visibility = VISIBLE
        }
    }

    private suspend fun fadeOut() {
        animate()
            .alpha(0f)
            .setDuration(300)
            .start()
        Timber.i("$type: Fading out...")
        delay(300)
    }

    private suspend fun fadeIn() {
        animate()
            .alpha(1f)
            .setDuration(300)
            .start()
        Timber.i("$type: Fading in...")
        delay(300)
    }

    private fun updateTextAndStyle() {
        val message = currentMessage.text

        // Font size
        currentMessage.textSize?.let { applyTextSize(it) }

        // Font weight
        currentMessage.textWeight?.let { applyTextWeight(it) }

        // Message
        if (message.isNotEmpty()) {
            Timber.i("$type: Set new message: '$message'")
            text = message
        } else {
            Timber.i("$type: Clearing message")
            text = null
        }
    }

    private fun applyVisibilityImmediate() {
        if (text.isNullOrBlank()) {
            alpha = 0f
            visibility = GONE
        } else {
            alpha = 1f
            visibility = VISIBLE
        }
    }

    private fun applyTextSize(sizeValue: Int) {
        val sizeInSp = sizeValue.toFloat()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp)
    }

    private fun applyTextWeight(weightValue: Int) {
        typeface = FontHelper.getTypeface(context, prefs.fontTypeface, weightValue)
    }
}
