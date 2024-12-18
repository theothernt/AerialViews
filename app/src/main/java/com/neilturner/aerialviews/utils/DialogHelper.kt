package com.neilturner.aerialviews.utils

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.neilturner.aerialviews.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DialogHelper {
    suspend fun show(
        context: Context,
        title: String,
        message: String,
    ) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(context).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create().show()
        }
    }
}
