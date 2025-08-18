package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.launch
import timber.log.Timber

class OverlaysWeatherForecastFragment : MenuStateFragment() {
    private val prefs: MutableList<ListPreference?> = mutableListOf()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_weather_forecast, rootKey)

        lifecycleScope.launch {
            setupPreferences()
            loadValues()
            setupChangeListeners()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Weather Forecast", this)
    }

    private fun setupPreferences() {
        listOf(
            "weather_forecast_slot1",
            "weather_forecast_slot2",
            "weather_forecast_slot3",
            "weather_forecast_slot4",
        ).forEach { name ->
            prefs.add(preferenceScreen.findPreference(name))
        }
    }

    private fun loadValues() {
        val items = GeneralPrefs.weatherForecast.split(",")
        items.forEachIndexed { index, item ->
            if (index < prefs.size && !item.isBlank()) {
                prefs[index]?.value = item
            }
        }
        Timber.i("Loaded weather forecast preferences: ${GeneralPrefs.weatherForecast}")
    }

    private fun setupChangeListeners() {
        val changeListener =
            Preference.OnPreferenceChangeListener { preference, newValue ->
                saveValues(preference as ListPreference, newValue.toString())
                true
            }

        prefs.forEach { preference ->
            preference?.onPreferenceChangeListener = changeListener
        }
    }

    private fun saveValues(
        changedPreference: ListPreference? = null,
        newValue: String = "",
    ) {
        val values =
            prefs.joinToString(",") { pref ->
                if (pref == changedPreference) {
                    newValue
                } else {
                    pref?.value ?: ""
                }
            }

        GeneralPrefs.weatherForecast = values
        Timber.i("Weather forecast preferences saved: $values")
    }
}
