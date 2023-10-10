package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.TextClock
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R

class AltTextClock : TextClock {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.ClockText)
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
