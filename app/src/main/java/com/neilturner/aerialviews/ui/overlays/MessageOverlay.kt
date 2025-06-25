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
import com.neilturner.aerialviews.services.MessageEvent
import com.neilturner.aerialviews.utils.FontHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.kosert.flowbus.EventsReceiver
import me.kosert.flowbus.subscribe
import timber.log.Timber

class MessageOverlay : AppCompatTextView {
    var type = OverlayType.MESSAGE1

    private val receiver = EventsReceiver()
    private var currentMessage = MessageEvent(messageNumber = 0)
    private var shouldUpdate = false
    private var isUpdating = false
    private val prefs = GeneralPrefs
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private var clearJob: Job? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
        visibility = GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        receiver.subscribe { messageEvent: MessageEvent ->
            Timber.i("$type: Received message event for slot ${messageEvent.messageNumber}")
            
            // Only process messages for this overlay's message number
            val overlayMessageNumber = getMessageNumberFromType()
            if (messageEvent.messageNumber == overlayMessageNumber) {
                Timber.i("$type: Processing message for slot $overlayMessageNumber")
                if (currentMessage != messageEvent) {
                    currentMessage = messageEvent
                    if (!isUpdating) {
                        mainScope.launch {
                            updateMessage()
                        }
                    } else {
                        shouldUpdate = true
                    }
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
        clearJob?.cancel()
    }

    private fun getMessageNumberFromType(): Int {
        return when (type) {
            OverlayType.MESSAGE1 -> 1
            OverlayType.MESSAGE2 -> 2
            OverlayType.MESSAGE3 -> 3
            OverlayType.MESSAGE4 -> 4
            else -> 1
        }
    }

    private suspend fun updateMessage() {
        isUpdating = true

        // Cancel any pending clear job
        clearJob?.cancel()

        if (alpha != 0f) {
            fadeOut()
        }

        shouldUpdate = false
        val shouldFadeIn = updateTextAndStyle()

        if (shouldFadeIn) {
            fadeIn()
            
            // Schedule auto-clear if duration is specified
            if (currentMessage.duration > 0) {
                clearJob = mainScope.launch {
                    delay(currentMessage.duration * 1000L)
                    if (text.isNotEmpty()) {
                        Timber.i("$type: Auto-clearing message after ${currentMessage.duration} seconds")
                        currentMessage = MessageEvent(
                            messageNumber = getMessageNumberFromType(),
                            text = "",
                            duration = 0
                        )
                        updateMessage()
                    }
                }
            }
        }

        animateOverlays()

        isUpdating = false

        if (shouldUpdate) {
            updateMessage()
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

    private fun updateTextAndStyle(): Boolean {
        val message = currentMessage.text
        
        // Apply dynamic text size
        applyTextSize(currentMessage.textSize)
        
        // Apply dynamic text weight
        applyTextWeight(currentMessage.textWeight)
        
        return if (message.isNotEmpty()) {
            Timber.i("$type: Set new message: '$message'")
            text = message
            true
        } else {
            Timber.i("$type: Clearing message")
            text = null
            false
        }
    }

    private fun applyTextSize(sizeString: String) {
        val sizeInSp = when (sizeString.lowercase()) {
            "small" -> prefs.messageSize.toFloat() * 0.8f
            "medium" -> prefs.messageSize.toFloat()
            "large" -> prefs.messageSize.toFloat() * 1.2f
            "xl" -> prefs.messageSize.toFloat() * 1.4f
            "xxl" -> prefs.messageSize.toFloat() * 1.6f
            else -> prefs.messageSize.toFloat()
        }
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeInSp)
        Timber.d("$type: Applied text size: $sizeString ($sizeInSp sp)")
    }

    private fun applyTextWeight(weightString: String) {
        val weight = when (weightString.lowercase()) {
            "light" -> "Light"
            "normal" -> "Normal"
            "bold" -> "Bold"
            "heavy" -> "Black"
            else -> prefs.messageWeight
        }
        typeface = FontHelper.getTypeface(context, prefs.fontTypeface, weight)
        Timber.d("$type: Applied text weight: $weightString -> $weight")
    }

    private fun animateOverlays() {
        val layout: ConstraintLayout? = parent as? ConstraintLayout

        layout?.let {
            TransitionManager.beginDelayedTransition(
                it,
                TransitionSet().apply {
                    ordering = TransitionSet.ORDERING_TOGETHER
                    addTransition(Fade())
                    addTransition(ChangeBounds())
                    duration = 300
                }
            )
        }

        if (!isGone && text.isNullOrBlank()) {
            Timber.i("$type: Transition... GONE")
            visibility = GONE
        } else if (isGone && !text.isNullOrEmpty()) {
            Timber.i("$type: Transition... VISIBLE")
            visibility = VISIBLE
        }
    }

    fun updateMessage(message: String) {
        // Legacy method for static message updates from preferences
        this.text = message
    }
}
