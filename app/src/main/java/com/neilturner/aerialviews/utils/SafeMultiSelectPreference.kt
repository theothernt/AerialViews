package com.neilturner.aerialviews.utils

import android.content.Context
import android.util.AttributeSet
import androidx.preference.MultiSelectListPreference

class SafeMultiSelectListPreference: MultiSelectListPreference {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr,
    )
    
    init {
        isIconSpaceReserved = true
    }

    override fun onClick() {
        if (entries.isNullOrEmpty()) {
            return
        }
        super.onClick()
    }
}