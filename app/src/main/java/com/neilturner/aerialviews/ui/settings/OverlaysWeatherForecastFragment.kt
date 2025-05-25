package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.services.weather.WeatherInfo
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import timber.log.Timber

class OverlaysWeatherForecastFragment : MenuStateFragment() {
    private val weatherForecastKey = "weather_forecast_info"
    private var dropdown1: ListPreference? = null
    private var dropdown2: ListPreference? = null
    private var dropdown3: ListPreference? = null
    private var dropdown4: ListPreference? = null

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_weather_forecast, rootKey)

        // Initialize dropdowns
        dropdown1 = preferenceScreen.findPreference<ListPreference>("weather_info_dropdown_1")
        dropdown2 = preferenceScreen.findPreference<ListPreference>("weather_info_dropdown_2")
        dropdown3 = preferenceScreen.findPreference<ListPreference>("weather_info_dropdown_3")
        dropdown4 = preferenceScreen.findPreference<ListPreference>("weather_info_dropdown_4")

        // Set up the dropdown entries and values from WeatherInfo enum
        setupDropdowns()

        // Load saved values
        loadSavedValues()

        // Set up preference change listeners
        setupPreferenceChangeListeners()
    }

    private fun setupDropdowns() {
        // Get WeatherInfo enum values as strings
        val weatherInfoValues = WeatherInfo.entries.map { it.name }
        val weatherInfoEntries = WeatherInfo.entries.map { formatEnumName(it.name) }

        // Set entries and values for all dropdowns
        val entries = weatherInfoEntries.toTypedArray()
        val entryValues = weatherInfoValues.toTypedArray()

        dropdown1?.entries = entries
        dropdown1?.entryValues = entryValues

        dropdown2?.entries = entries
        dropdown2?.entryValues = entryValues

        dropdown3?.entries = entries
        dropdown3?.entryValues = entryValues

        dropdown4?.entries = entries
        dropdown4?.entryValues = entryValues

        // Set default values if not already set
        if (dropdown1?.value == null) dropdown1?.value = WeatherInfo.ICON.name
        if (dropdown2?.value == null) dropdown2?.value = WeatherInfo.SUMMARY.name
        if (dropdown3?.value == null) dropdown3?.value = WeatherInfo.TEMPERATURE.name
        if (dropdown4?.value == null) dropdown4?.value = WeatherInfo.CITY.name
    }

    private fun formatEnumName(name: String): String {
        // Convert ENUM_NAME to "Enum Name" format
        return name.replace("_", " ").split(" ")
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    private fun loadSavedValues() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val savedList = sharedPreferences.getString(weatherForecastKey, null)

        if (savedList != null) {
            val items = savedList.split(",")
            if (items.size >= 4) {
                dropdown1?.value = items[0]
                dropdown2?.value = items[1]
                dropdown3?.value = items[2]
                dropdown4?.value = items[3]
            }
        }
    }

    private fun setupPreferenceChangeListeners() {
        val preferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            saveWeatherInfoList()
            true
        }

        dropdown1?.onPreferenceChangeListener = preferenceChangeListener
        dropdown2?.onPreferenceChangeListener = preferenceChangeListener
        dropdown3?.onPreferenceChangeListener = preferenceChangeListener
        dropdown4?.onPreferenceChangeListener = preferenceChangeListener
    }

    private fun saveWeatherInfoList() {
        val concatenatedList = listOf(
            dropdown1?.value,
            dropdown2?.value,
            dropdown3?.value,
            dropdown4?.value
        ).joinToString(",")

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        sharedPreferences.edit { putString(weatherForecastKey, concatenatedList) }

        Timber.i("Weather forecast preferences saved: $concatenatedList")

    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Weather Forecast", this)
    }
}
