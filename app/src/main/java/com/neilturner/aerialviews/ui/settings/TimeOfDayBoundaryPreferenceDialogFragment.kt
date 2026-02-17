package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.preference.PreferenceDialogFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs

class TimeOfDayBoundaryPreferenceDialogFragment : PreferenceDialogFragmentCompat() {
    private val dayIncludes = GeneralPrefs.playlistTimeOfDayDayIncludes.toMutableSet()
    private val nightIncludes = GeneralPrefs.playlistTimeOfDayNightIncludes.toMutableSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            dayIncludes.clear()
            dayIncludes.addAll(savedInstanceState.getStringArrayList(STATE_DAY_INCLUDES) ?: emptyList())
            nightIncludes.clear()
            nightIncludes.addAll(savedInstanceState.getStringArrayList(STATE_NIGHT_INCLUDES) ?: emptyList())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_DAY_INCLUDES, ArrayList(dayIncludes))
        outState.putStringArrayList(STATE_NIGHT_INCLUDES, ArrayList(nightIncludes))
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val daySunrise = view.findViewById<CheckBox>(R.id.checkbox_day_sunrise)
        val daySunset = view.findViewById<CheckBox>(R.id.checkbox_day_sunset)
        val nightSunrise = view.findViewById<CheckBox>(R.id.checkbox_night_sunrise)
        val nightSunset = view.findViewById<CheckBox>(R.id.checkbox_night_sunset)

        bindCheckbox(daySunrise, dayIncludes, "SUNRISE")
        bindCheckbox(daySunset, dayIncludes, "SUNSET")
        bindCheckbox(nightSunrise, nightIncludes, "SUNRISE")
        bindCheckbox(nightSunset, nightIncludes, "SUNSET")
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (!positiveResult) {
            return
        }

        GeneralPrefs.playlistTimeOfDayDayIncludes.clear()
        GeneralPrefs.playlistTimeOfDayDayIncludes.addAll(dayIncludes)
        GeneralPrefs.playlistTimeOfDayNightIncludes.clear()
        GeneralPrefs.playlistTimeOfDayNightIncludes.addAll(nightIncludes)
    }

    private fun bindCheckbox(
        checkBox: CheckBox,
        includes: MutableSet<String>,
        value: String,
    ) {
        checkBox.isChecked = includes.contains(value)
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                includes.add(value)
            } else {
                includes.remove(value)
            }
        }
    }

    companion object {
        private const val STATE_DAY_INCLUDES = "state_day_includes"
        private const val STATE_NIGHT_INCLUDES = "state_night_includes"

        fun newInstance(key: String): TimeOfDayBoundaryPreferenceDialogFragment {
            val fragment = TimeOfDayBoundaryPreferenceDialogFragment()
            val bundle = Bundle(1)
            bundle.putString(ARG_KEY, key)
            fragment.arguments = bundle
            return fragment
        }
    }
}
