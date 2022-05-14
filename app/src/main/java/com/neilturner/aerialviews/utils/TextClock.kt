package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.TextClock

class TextClock : TextClock {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun onDetachedFromWindow() {
        try {
            super.onDetachedFromWindow()
        } catch (e: Exception) {
            Log.e("TextClock", e.message, e.cause)
        }
    }
}
