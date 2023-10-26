package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.TextClock
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ClockType

class AltTextClock : TextClock {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    fun updateFormat(format: ClockType) {
        if (format == ClockType.HOUR_12) {
            this.format12Hour = "h:mm a"
            this.format24Hour = "h:mm a"
            this.text.toString().lowercase()
        }

        if (format == ClockType.HOUR_24) {
            this.format12Hour = "HH:mm"
            this.format24Hour = "HH:mm"
        }
    }

    override fun onDetachedFromWindow() {
        // Fixes a commonly reported crash with this control ?!
        try {
            super.onDetachedFromWindow()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e.cause)
        }
    }

    companion object {
        private const val TAG = "AltTextClock"
    }
}
