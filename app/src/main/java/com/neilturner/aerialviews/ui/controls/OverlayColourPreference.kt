package com.neilturner.aerialviews.ui.controls

import android.content.Context
import android.content.res.TypedArray
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.ui.helpers.ColourHelper

/**
 * Lets the user pick the overlay colour by typing a hex/CSS colour, with a live preview of the
 * result. Deliberately extends [Preference] rather than `EditTextPreference` so MenuStateFragment
 * does not swap it for the TV text dialog.
 */
class OverlayColourPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    ) : Preference(context, attrs, defStyleAttr) {
        init {
            isPersistent = true
        }

        // A plain Preference never asks for a dialog on click; only DialogPreference subclasses do
        override fun performClick() {
            super.performClick()
            getPreferenceManager()?.showDialog(this)
        }

        override fun onGetDefaultValue(
            a: TypedArray,
            index: Int,
        ): Any = a.getString(index).orEmpty().validColourOrNull() ?: ColourHelper.DEFAULT_OVERLAY_COLOUR_HEX

        override fun onSetInitialValue(defaultValue: Any?) {
            val fallback =
                (defaultValue as? String).orEmpty().validColourOrNull()
                    ?: ColourHelper.DEFAULT_OVERLAY_COLOUR_HEX
            persistString(getPersistedString(fallback))
        }

        override fun getSummary(): CharSequence = ColourHelper.toHexString(resolveColour())

        private fun resolveColour(): Int = ColourHelper.overlayColour(getPersistedString(ColourHelper.DEFAULT_OVERLAY_COLOUR_HEX))

        private fun String?.validColourOrNull(): String? = this?.takeIf { ColourHelper.parseOpaqueColour(it) != null }

        private var dialog: AlertDialog? = null

        fun showDialog() {
            // D-pad OK auto-repeat would otherwise stack dialogs
            if (dialog?.isShowing == true) {
                return
            }

            val view = LayoutInflater.from(context).inflate(R.layout.dialog_overlay_colour, null)
            val previewText = view.findViewById<TextView>(R.id.colour_preview_text)
            val hexInput = view.findViewById<EditText>(R.id.colour_hex_input)
            val error = view.findViewById<TextView>(R.id.colour_error)

            val colourDialog =
                AlertDialog
                    .Builder(context)
                    .setTitle(title)
                    .setView(view)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create()

            val selected = resolveColour()
            previewText.setTextColor(selected)
            hexInput.setText(ColourHelper.toHexString(selected))
            hexInput.setSelection(hexInput.text.length)

            // Live preview while typing; invalid values keep the last good colour
            hexInput.addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        val parsed = ColourHelper.parseOpaqueColour(s?.toString())
                        if (parsed != null) {
                            previewText.setTextColor(parsed)
                            error.visibility = View.GONE
                        } else {
                            error.visibility = View.VISIBLE
                        }
                    }
                },
            )

            colourDialog.show()
            dialog = colourDialog
            colourDialog.setOnDismissListener { dialog = null }

            // Keep the dialog open when the typed colour is invalid
            colourDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = ColourHelper.parseOpaqueColour(hexInput.text.toString())
                if (parsed == null) {
                    error.visibility = View.VISIBLE
                } else {
                    applyColour(parsed)
                    colourDialog.dismiss()
                }
            }
        }

        // Called from the hosting fragment so the dialog cannot outlive it
        fun dismissDialog() {
            dialog?.dismiss()
            dialog = null
        }

        private fun applyColour(colour: Int) {
            val hex = ColourHelper.toHexString(colour)
            if (callChangeListener(hex)) {
                persistString(hex)
                notifyChanged()
            }
        }
    }
