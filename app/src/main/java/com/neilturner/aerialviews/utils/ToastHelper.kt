package com.neilturner.aerialviews.utils

import android.content.Context
import android.widget.Toast

object ToastHelper {
    fun show(
        context: Context,
        message: String,
    ) {
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG,
        ).show()
    }
}
