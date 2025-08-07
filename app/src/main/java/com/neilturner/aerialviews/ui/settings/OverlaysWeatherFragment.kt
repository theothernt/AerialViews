package com.neilturner.aerialviews.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.DialogLocationSearchBinding
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.services.weather.LocationResponse
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
            locationPreference = findPreference("weather_location_name")
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
        val binding = DialogLocationSearchBinding.inflate(layoutInflater)

        AlertDialog
            .Builder(requireContext())
            .setTitle("Search for a location or enter GPS coordinates")
            .setView(binding.root)
            .setPositiveButton("Search") { _, _ ->
                val query =
                    binding.editLocationSearch.text
                        .toString()
                        .trim()
                if (query.isNotEmpty()) {
                    searchLocation(query)
                }
            }.setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchLocation(query: String) {
        // Check if the query contains GPS coordinates (lat,lon format)
        val gpsPattern = Regex("""^(-?\d+\.?\d*),\s*(-?\d+\.?\d*)$""")
        val matchResult = gpsPattern.find(query.trim())

        if (matchResult != null) {
            // Handle GPS coordinates directly
            try {
                val lat = matchResult.groupValues[1].toDouble()
                val lon = matchResult.groupValues[2].toDouble()

                // Validate coordinate ranges
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    val coordinateLocation = LocationResponse(
                        name = "Custom Location",
                        lat = lat,
                        lon = lon,
                        country = "",
                        state = null
                    )
                    saveLocation(coordinateLocation)
                    return
                } else {
                    DialogHelper.show(
                        requireContext(),
                        "Invalid Coordinates",
                        "Please enter valid coordinates. Latitude must be between -90 and 90, longitude between -180 and 180."
                    )
                    return
                }
            } catch (e: NumberFormatException) {
                DialogHelper.show(
                    requireContext(),
                    "Invalid Format",
                    "Please enter coordinates in the format: latitude,longitude (e.g., 40.7128,-74.0060)"
                )
                return
            }
        }

        // Original location search logic
        val loadingMessage = getString(R.string.weather_location_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
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

    private fun showLocationSelectionDialog(locations: List<LocationResponse>) {
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

    private fun saveLocation(location: LocationResponse) {
        val prefs = GeneralPrefs
        prefs.weatherLocationName = location.getDisplayName()
        prefs.weatherLocationLat = location.lat.toString()
        prefs.weatherLocationLon = location.lon.toString()
        updateSummary(location.getDisplayName())
    }
}
