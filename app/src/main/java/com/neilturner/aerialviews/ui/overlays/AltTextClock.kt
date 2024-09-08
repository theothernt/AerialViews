package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
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

    fun updateFormat(format: ClockType?) {
        when (format) {
            ClockType.HOUR_24 -> {
                this.format12Hour = "HH:mm"
                this.format24Hour = "HH:mm"
            }
            ClockType.HOUR_12 -> {
                this.format12Hour = "h:mm a"
                this.format24Hour = "h:mm a"
            }
            else -> {
                // this.text.toString().lowercase() // maybe add pref? 1:30pm vs 1:30PM
            }
        }
    }

    override fun onDetachedFromWindow() {
        // Fixes a commonly reported crash with this control ?!
        try {
            super.onDetachedFromWindow()
        } catch (ex: Exception) {
            // Doesn't matter
        }
    }
}
