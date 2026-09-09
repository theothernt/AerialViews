package com.neilturner.aerialviews.ui.controls

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import androidx.preference.ListPreference
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TimeListPreference
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.dialogPreferenceStyle,
    ) : ListPreference(context, attrs, defStyleAttr) {
        init {
            populateEntries()
        }

        private fun populateEntries() {
            val is24Hour = DateFormat.is24HourFormat(context)
            val formatter =
                if (is24Hour) {
                    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                } else {
                    DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                }

            val entriesList = mutableListOf<String>()
            val valuesList = mutableListOf<String>()

            for (hour in 0..23) {
                for (minute in listOf(0, 15, 30, 45)) {
                    val time = LocalTime.of(hour, minute)
                    val rawValue = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                    val displayLabel = time.format(formatter)

                    entriesList.add(displayLabel)
                    valuesList.add(rawValue)
                }
            }

            entries = entriesList.toTypedArray()
            entryValues = valuesList.toTypedArray()
        }
    }
