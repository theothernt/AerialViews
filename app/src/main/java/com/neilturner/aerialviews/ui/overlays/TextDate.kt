@file:Suppress("unused")

package com.neilturner.aerialviews.ui.overlays

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.widget.TextViewCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.DateType
import com.neilturner.aerialviews.utils.DateHelper

class TextDate : AppCompatTextView {

    private var refreshDateHandler: (() -> Unit)? = null
    private var type = DateType.COMPACT
    private var custom = ""

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        TextViewCompat.setTextAppearance(this, R.style.OverlayText)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        refreshDateHandler = null
    }

    fun updateFormat(type: DateType, custom: String) {
        this.type = type
        this.custom = custom
        refreshDate()
    }

    private fun refreshDate() {
        refreshDateHandler = {
            this.text = DateHelper.formatDate(context, type, custom)
            this.postDelayed({
                refreshDateHandler?.let { it() }
            }, 1000)
        }
        this.postDelayed({
            refreshDateHandler?.let { it() }
        }, 1000)
    }

    companion object {
        private const val TAG = "TextDate"
    }
}
