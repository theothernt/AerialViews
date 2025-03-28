package com.neilturner.aerialviews.utils

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
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

    fun progressDialog(
        context: Context,
        loadingMessage: String,
    ): AlertDialog {
        val layoutInflater = LayoutInflater.from(context)
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        dialogView.findViewById<TextView>(R.id.loading_message).text = loadingMessage
        return AlertDialog
            .Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()
    }
}
