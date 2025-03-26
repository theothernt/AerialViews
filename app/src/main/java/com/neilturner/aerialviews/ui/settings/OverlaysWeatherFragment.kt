package com.neilturner.aerialviews.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.WeatherService
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class OverlaysWeatherFragment : MenuStateFragment() {
    private lateinit var weatherService: WeatherService
    private var locationPreference: Preference? = null

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_weather, rootKey)
        weatherService = WeatherService(requireContext())

        lifecycleScope.launch {
            updateSummary()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Weather", this)
    }

    private fun updateSummary(text: String = "") {
        if (locationPreference == null) {
            locationPreference = findPreference<Preference>("weather_location_name")
            locationPreference?.setOnPreferenceClickListener {
                showLocationSearchDialog()
                true
            }
        }

        if (text.isNotEmpty()) {
            locationPreference?.summary = text
        } else {
            locationPreference?.summary =
                GeneralPrefs.weatherLocationName.ifEmpty {
                    "Not set"
                }
        }
    }

    private fun showLocationSearchDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_location_search, null)
        val input = dialogView.findViewById<EditText>(R.id.edit_location_search)

        AlertDialog
            .Builder(requireContext())
            .setTitle("Search for a location")
            .setView(dialogView)
            .setPositiveButton("Search") { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchLocation(query)
                }
            }.setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchLocation(query: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_progress, null)
        val progressDialog =
            AlertDialog
                .Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create()

        progressDialog.show()

        lifecycleScope.launch {
            try {
                val locations =
                    withContext(Dispatchers.IO) {
                        weatherService.lookupLocation(query)
                    }

                progressDialog.dismiss()

                if (locations.isEmpty()) {
                    DialogHelper.show(
                        requireContext(),
                        "No Results",
                        "No locations found matching your search.",
                    )
                } else {
                    showLocationSelectionDialog(locations)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to search for location")
                progressDialog.dismiss()
                DialogHelper.show(requireContext(), "Error", "Failed to search for location.")
            }
        }
    }

    private fun showLocationSelectionDialog(locations: List<WeatherService.LocationResponse>) {
        val locationNames = locations.map { it.getDisplayName() }.toTypedArray()

        AlertDialog
            .Builder(requireContext())
            .setTitle("Select Location")
            .setItems(locationNames) { _, which ->
                val selectedLocation = locations[which]
                saveLocation(selectedLocation)
            }.setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun saveLocation(location: WeatherService.LocationResponse) {
        val prefs = GeneralPrefs
        prefs.weatherLocationName = location.getDisplayName()
        prefs.weatherLocationLat = location.lat.toString()
        prefs.weatherLocationLon = location.lon.toString()
        updateSummary(location.getDisplayName())
    }
}
