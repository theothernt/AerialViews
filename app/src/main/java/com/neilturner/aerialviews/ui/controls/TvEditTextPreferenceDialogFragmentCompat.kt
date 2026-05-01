package com.neilturner.aerialviews.ui.controls

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreferenceDialogFragmentCompat

class TvEditTextPreferenceDialogFragmentCompat : EditTextPreferenceDialogFragmentCompat() {

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)
        val editText = view.findViewById<EditText>(android.R.id.edit)
        editText?.apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val alertDialog = dialog as? AlertDialog
                    alertDialog?.getButton(DialogInterface.BUTTON_POSITIVE)?.performClick()
                    true
                } else false
            }
        }
    }

    companion object {
        fun newInstance(key: String): TvEditTextPreferenceDialogFragmentCompat {
            val fragment = TvEditTextPreferenceDialogFragmentCompat()
            val b = Bundle(1)
            b.putString(ARG_KEY, key)
            fragment.arguments = b
            return fragment
        }
    }
}
