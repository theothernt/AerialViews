package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysWeatherFragment : MenuStateFragment() {
    private var locationPreference: Preference? = null

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_weather, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Weather", this)
        updateSummary()
    }

    private fun updateSummary() {
        if (locationPreference == null) {
            locationPreference = findPreference("weather_location_name")
        }

        locationPreference?.summary =
            GeneralPrefs.weatherLocationName.ifEmpty {
                getString(R.string.location_not_set)
            }
    }
}
