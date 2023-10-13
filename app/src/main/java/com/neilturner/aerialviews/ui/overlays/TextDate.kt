package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.utils.DateHelper
import java.lang.Exception
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TextDate : AppCompatTextView {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    fun updateFormat(type: DateType, custom: String) {
        this.text = DateHelper.formatDate(type, custom)
    }

    companion object {
        private const val TAG = "TextDate"
    }
}
