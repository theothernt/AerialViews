package com.neilturner.aerialviews.utils

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.DialogProgressBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DialogHelper {
    suspend fun showOnMain(
        context: Context,
        title: String,
        message: String,
    ) = withContext(Dispatchers.Main) {
        show(context, title, message)
    }

    fun show(
        context: Context,
        title: String,
        message: String,
    ) {
        AlertDialog.Builder(context).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create()
            show()
        }
    }

    fun progressDialog(
        context: Context,
        loadingMessage: String,
    ): AlertDialog {
        val binding = DialogProgressBinding.inflate(LayoutInflater.from(context))
        binding.loadingMessage.text = loadingMessage
        return AlertDialog
            .Builder(context)
            .setView(binding.root)
            .setCancelable(false)
            .create()
    }
}
