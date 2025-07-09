package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
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
    private var currentMessage = MessageEvent(0, "", 0, 0, 0)
    private var defaultMessage: String = ""
    private var hasReceivedApiMessage = false
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
        // Don't hide overlay initially - let default message show if present
        visibility = VISIBLE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        receiver.subscribe { messageEvent: MessageEvent ->
            Timber.i("$type: Received message event for slot ${messageEvent.messageNumber}")

            // TODO: add throttling
            // https://github.com/Kotlin/kotlinx.coroutines/issues/1446#issuecomment-1198103541

            // Only process messages for this overlay's message number
            val overlayMessageNumber = getMessageNumberFromType()
            if (messageEvent.messageNumber == overlayMessageNumber) {
                Timber.i("$type: Processing message for slot $overlayMessageNumber")
                if (currentMessage != messageEvent) {
                    hasReceivedApiMessage = true
                    currentMessage = messageEvent
                    updateMessage()
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        receiver.unsubscribe()
        clearJob?.cancel()
    }

    private fun getMessageNumberFromType(): Int = type.name.last().digitToInt()

    private fun updateMessage() {
        clearJob?.cancel()
        
        // If we have received an API message, use it
        if (hasReceivedApiMessage) {
            updateTextAndStyle()
            
            // If the API message is empty, fall back to default message
            if (currentMessage.text.isEmpty()) {
                if (defaultMessage.isNotEmpty()) {
                    text = defaultMessage
                    visibility = VISIBLE
                } else {
                    text = null
                    visibility = GONE
                }
            } else {
                visibility = if (text.isNullOrBlank()) GONE else VISIBLE
            }

            // Schedule auto-clear if duration is specified
            if (currentMessage.duration > 0) {
                clearJob =
                    mainScope.launch {
                        delay(currentMessage.duration * 1000L)
                        if (text.isNotEmpty()) {
                            Timber.i("$type: Auto-clearing message after ${currentMessage.duration} seconds")
                            currentMessage = currentMessage.copy(text = "", duration = 0)
                            updateMessage()
                        }
                    }
            }
        } else {
            // No API message received yet, keep default message if present
            if (defaultMessage.isNotEmpty()) {
                text = defaultMessage
                visibility = VISIBLE
            } else {
                visibility = GONE
            }
        }
    }

    private fun updateTextAndStyle() {
        val message = currentMessage.text

        // Font size - only apply if we have an API message with size specified
        if (hasReceivedApiMessage && currentMessage.textSize != null) {
            applyTextSize(currentMessage.textSize!!)
        }

        // Font weight - only apply if we have an API message with weight specified
        if (hasReceivedApiMessage && currentMessage.textWeight != null) {
            applyTextWeight(currentMessage.textWeight!!)
        }

        // Message content
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

    fun resetToDefaultMessage() {
        // Reset API message state and return to default message
        hasReceivedApiMessage = false
        currentMessage = MessageEvent(0, "", 0, 0, 0)
        clearJob?.cancel()
        
        if (defaultMessage.isNotEmpty()) {
            text = defaultMessage
            visibility = VISIBLE
        } else {
            text = null
            visibility = GONE
        }
    }

    fun updateMessage(message: String) {
        // Legacy method for static message updates from preferences
        // This should only be called for default messages, not API messages
        defaultMessage = message
        
        // Only set the default message if we haven't received any API message yet
        if (!hasReceivedApiMessage) {
            if (message.isNotEmpty()) {
                text = message
                visibility = VISIBLE
            } else {
                text = null
                visibility = GONE
            }
        }
    }
}
