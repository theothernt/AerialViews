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

class LocationFragment : MenuStateFragment() {
    private lateinit var weatherService: WeatherService
    private var locationPreference: Preference? = null

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_location, rootKey)
        weatherService = WeatherService(requireContext())

        lifecycleScope.launch {
            updateSummary()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Location", this)
    }

    private fun updateSummary(text: String = "") {
        if (locationPreference == null) {
            locationPreference = findPreference("weather_location_name")
            locationPreference?.setOnPreferenceClickListener {
                showLocationSearchDialog()
                true
            }
        }

        locationPreference?.summary =
            GeneralPrefs.weatherLocationName.ifEmpty {
                getString(R.string.location_not_set)
            }
    }

    private fun showLocationSearchDialog() {
        val binding = DialogLocationSearchBinding.inflate(layoutInflater)

        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.location_search_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.button_search) { _, _ ->
                val query =
                    binding.editLocationSearch.text
                        .toString()
                        .trim()
                if (query.isNotEmpty()) {
                    searchLocation(query)
                }
            }.setNegativeButton(R.string.button_cancel, null)
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
                    searchLocationByCoordinates(lat, lon)
                    return
                } else {
                    DialogHelper.show(
                        requireContext(),
                        getString(R.string.dialog_error_title),
                        getString(R.string.location_error_invalid_coordinates),
                    )
                    return
                }
            } catch (e: NumberFormatException) {
                DialogHelper.show(
                    requireContext(),
                    getString(R.string.dialog_error_title),
                    getString(R.string.location_error_format),
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
                        getString(R.string.dialog_no_results_title),
                        getString(R.string.dialog_no_results_message),
                    )
                } else {
                    showLocationSelectionDialog(locations)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to search for location")
                progressDialog.dismiss()
                DialogHelper.show(
                    requireContext(),
                    getString(R.string.dialog_error_title),
                    getString(R.string.dialog_error_search_message, e.message),
                )
            }
        }
    }

    private fun searchLocationByCoordinates(
        lat: Double,
        lon: Double,
    ) {
        val loadingMessage = getString(R.string.location_lookup_coordinates)
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
                        weatherService.lookupLocationByCoordinates(lat, lon)
                    }

                progressDialog.dismiss()

                if (locations.isEmpty()) {
                    // If no location found via API, create a custom location with coordinates
                    val coordinateLocation =
                        LocationResponse(
                            name = getString(R.string.location_custom_name_default),
                            lat = lat,
                            lon = lon,
                            country = "",
                            state = null,
                        )
                    showLocationSelectionDialog(listOf(coordinateLocation))
                } else {
                    // Update the coordinates in the API results to match the exact input
                    val updatedLocations =
                        locations.map { location ->
                            location.copy(lat = lat, lon = lon)
                        }
                    showLocationSelectionDialog(updatedLocations)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to lookup location by coordinates")
                progressDialog.dismiss()

                // Fallback: create a custom location if API fails
                val coordinateLocation =
                    LocationResponse(
                        name = getString(R.string.location_custom_name_default),
                        lat = lat,
                        lon = lon,
                        country = "",
                        state = null,
                    )
                showLocationSelectionDialog(listOf(coordinateLocation))
            }
        }
    }

    private fun showLocationSelectionDialog(locations: List<LocationResponse>) {
        val locationNames = locations.map { it.getDisplayName() }.toTypedArray()

        AlertDialog
            .Builder(requireContext())
            .setTitle(R.string.location_selection_title)
            .setItems(locationNames) { _, which ->
                val selectedLocation = locations[which]
                saveLocation(selectedLocation)
            }.setNegativeButton(R.string.button_cancel, null)
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
