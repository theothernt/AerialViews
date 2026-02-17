package com.neilturner.aerialviews.ui.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.DialogPreference
import com.neilturner.aerialviews.R

class TimeOfDayBoundaryDialogPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : DialogPreference(context, attrs) {
        init {
            dialogLayoutResource = R.layout.dialog_time_of_day_boundary
            positiveButtonText = context.getString(android.R.string.ok)
            negativeButtonText = context.getString(android.R.string.cancel)
        }
    }
