package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.OverlayType
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.ui.overlays.state.MessageOverlayState
import com.neilturner.aerialviews.utils.FontHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class MessageOverlay : AppCompatTextView {
    var type = OverlayType.EMPTY

    private var currentMessage = MessageOverlayState()
    private val prefs = GeneralPrefs
    private val mainScope = CoroutineScope(Dispatchers.Main)
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

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearJob?.cancel()
    }

    fun message(message: String) {
        text = message
    }

    fun render(state: MessageOverlayState) {
        if (currentMessage != state) {
            currentMessage = state
            updateMessage()
        }
    }

    private fun updateMessage() {
        clearJob?.cancel()
        updateTextAndStyle()
        // visibility = if (text.isNullOrBlank()) GONE else VISIBLE

        // Schedule auto-clear if duration is specified
        currentMessage.duration?.let {
            if (it > 0) {
                clearJob =
                    mainScope.launch {
                        delay(it * 1000L)
                        if (text.isNotEmpty()) {
                            Timber.i("$type: Auto-clearing message after $it seconds")
                            currentMessage = currentMessage.copy(text = "", duration = 0)
                            updateMessage()
                        }
                    }
            }
        }
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

    private fun applyTextSize(sizeValue: Int) {
        val sizeInSp = sizeValue.toFloat()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp)
    }

    private fun applyTextWeight(weightValue: Int) {
        typeface = FontHelper.getTypeface(context, prefs.fontTypeface, weightValue)
    }
}
