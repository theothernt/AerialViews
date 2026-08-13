package com.neilturner.aerialviews.ui.controls

import android.app.AlertDialog
import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.NumberPicker
import androidx.preference.DialogPreference
import com.neilturner.aerialviews.R

class TimePickerDialogPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    ) : DialogPreference(context, attrs, defStyleAttr) {

        private var defaultValue = "00:00"

        override fun onGetDefaultValue(
            a: TypedArray,
            index: Int,
        ): Any {
            return a.getString(index) ?: "00:00"
        }

        override fun onSetInitialValue(defaultValue: Any?) {
            this.defaultValue = (defaultValue as? String) ?: "00:00"
        }

        override fun onClick() {
            showDialog()
        }

        private fun showDialog() {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_time_picker, null)
            val hoursPicker = view.findViewById<NumberPicker>(R.id.hours_picker)
            val minutesPicker = view.findViewById<NumberPicker>(R.id.minutes_picker)

            hoursPicker.minValue = 0
            hoursPicker.maxValue = 23
            hoursPicker.setFormatter { i -> String.format("%02d", i) }

            minutesPicker.minValue = 0
            minutesPicker.maxValue = 59
            minutesPicker.setFormatter { i -> String.format("%02d", i) }

            val currentTime = getPersistedString(defaultValue)
            val parts = currentTime.split(":")
            val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

            hoursPicker.value = initialHour.coerceIn(0, 23)
            minutesPicker.value = initialMinute.coerceIn(0, 59)

            AlertDialog
                .Builder(context)
                .setTitle(dialogTitle ?: title ?: "")
                .setView(view)
                .setPositiveButton(R.string.button_ok) { _, _ ->
                    hoursPicker.clearFocus()
                    minutesPicker.clearFocus()
                    val selectedTime = String.format("%02d:%02d", hoursPicker.value, minutesPicker.value)
                    persistString(selectedTime)
                    notifyChanged()
                }.setNegativeButton(R.string.button_cancel, null)
                .show()
        }
    }
