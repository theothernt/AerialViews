package com.neilturner.aerialviews.utils

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ToastHelper {
    suspend fun show(context: Context, message: String, toastLength: Int = Toast.LENGTH_LONG) = withContext(Dispatchers.Main) {
        Toast.makeText(
            context,
            message,
            toastLength
        ).show()
    }
}
