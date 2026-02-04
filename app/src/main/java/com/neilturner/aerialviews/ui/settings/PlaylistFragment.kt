@file:Suppress("SameParameterValue")

package com.neilturner.aerialviews.ui.settings

import android.location.Address
import android.location.Geocoder
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.databinding.DialogLocationSearchBinding
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_playlist, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Playlist", this)
        updateAllSummaries()
    }

    private fun updateAllSummaries() {
        val maxLengthPref = findPreference<ListPreference>("playback_max_video_length")
        maxLengthPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                toggleControls(newValue as String)
                true
            }
        toggleControls(maxLengthPref?.value as String)
        setupSummaryUpdater("limit_longer_videos", R.array.limit_Longer_videos_summaries)

        val autoTimeOfDayPref = findPreference<CheckBoxPreference>("playlist_auto_time_of_day")
        autoTimeOfDayPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateLocationEnabledState(newValue as Boolean)
                true
            }

        updateLocationEnabledState(autoTimeOfDayPref?.isChecked ?: false)

        val randomStartPref = findPreference<ListPreference>("random_start_position_range")
        randomStartPref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                randomStartPref.summary = "0%% - $newValue%%"
                true
            }
        randomStartPref?.summary = "0%% - ${randomStartPref.value}%%"

        setupLocationPreference()
    }

    private fun updateLocationEnabledState(enabled: Boolean) {
        val locationPreference = findPreference<Preference>("playlist_location_name")
        locationPreference?.isEnabled = enabled
    }

    private fun setupLocationPreference() {
        val locationPreference = findPreference<Preference>("playlist_location_name")
        locationPreference?.setOnPreferenceClickListener {
            showLocationSearchDialog()
            true
        }
        updateLocationSummary()
    }

    private fun updateLocationSummary() {
        val locationPreference = findPreference<Preference>("playlist_location_name")
        locationPreference?.summary = getString(R.string.playlist_set_location_summary, GeneralPrefs.playlistLocationName.ifEmpty { getString(R.string.location_not_set) })
    }

    private fun showLocationSearchDialog() {
        val binding = DialogLocationSearchBinding.inflate(layoutInflater)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_search_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.button_search) { _, _ ->
                val query = binding.editLocationSearch.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchLocation(query)
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun searchLocation(query: String) {
        val gpsPattern = Regex("""^(-?\d+\.?\d*),\s*(-?\d+\.?\d*)$""")
        val matchResult = gpsPattern.find(query.trim())

        if (matchResult != null) {
            try {
                val lat = matchResult.groupValues[1].toDouble()
                val lon = matchResult.groupValues[2].toDouble()
                if (lat in -90.0..90.0 && lon in -180.0..180.0) {
                    searchLocationByCoordinates(lat, lon)
                    return
                }
            } catch (e: NumberFormatException) {
                // Ignore and fall through to name search
            }
        }

        val loadingMessage = getString(R.string.weather_location_searching)
        val progressDialog = DialogHelper.progressDialog(requireContext(), loadingMessage)
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val geocoder = Geocoder(requireContext())
                val addresses = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(query, 5)
                }
                progressDialog.dismiss()
                if (addresses.isNullOrEmpty()) {
                    DialogHelper.show(requireContext(), getString(R.string.dialog_no_results_title), getString(R.string.dialog_no_results_message))
                } else {
                    showLocationSelectionDialogFromAddresses(addresses)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                DialogHelper.show(requireContext(), getString(R.string.dialog_error_title), getString(R.string.dialog_error_search_message, e.message))
            }
        }
    }

    private fun searchLocationByCoordinates(lat: Double, lon: Double) {
        val loadingMessage = getString(R.string.location_lookup_coordinates)
        val progressDialog = DialogHelper.progressDialog(requireContext(), loadingMessage)
        progressDialog.show()

        lifecycleScope.launch {
            try {
                val geocoder = Geocoder(requireContext())
                val addresses = withContext(Dispatchers.IO) {
                    geocoder.getFromLocation(lat, lon, 1)
                }
                progressDialog.dismiss()
                if (addresses.isNullOrEmpty()) {
                    saveLocation(lat, lon, getString(R.string.location_custom_name, lat.toString(), lon.toString()))
                } else {
                    showLocationSelectionDialogFromAddresses(addresses)
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                saveLocation(lat, lon, getString(R.string.location_custom_name, lat.toString(), lon.toString()))
            }
        }
    }

    private fun showLocationSelectionDialogFromAddresses(addresses: List<Address>) {
        val locationNames = addresses.map { address ->
            val sb = StringBuilder()
            for (i in 0..address.maxAddressLineIndex) {
                sb.append(address.getAddressLine(i))
                if (i < address.maxAddressLineIndex) sb.append(", ")
            }
            if (sb.isEmpty()) {
                val city = address.locality ?: address.subAdminArea ?: address.adminArea ?: address.countryName ?: getString(R.string.location_unknown)
                sb.append(city)
                if (address.countryName != null && city != address.countryName) {
                    sb.append(", ").append(address.countryName)
                }
            }
            sb.toString()
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.location_selection_title)
            .setItems(locationNames) { _, which ->
                val selectedAddress = addresses[which]
                saveLocation(selectedAddress.latitude, selectedAddress.longitude, locationNames[which])
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun saveLocation(lat: Double, lon: Double, name: String) {
        GeneralPrefs.playlistLocationName = name
        GeneralPrefs.playlistLocationLat = lat.toString()
        GeneralPrefs.playlistLocationLon = lon.toString()
        updateLocationSummary()
    }

    private fun toggleControls(value: String) {
        val shortVideosPref = findPreference<CheckBoxPreference>("loop_short_videos")
        val longVideosPref = findPreference<ListPreference>("limit_longer_videos")
        val randomStartPref = findPreference<CheckBoxPreference>("random_start_position")

        if (value == "0") {
            shortVideosPref?.isEnabled = false
            longVideosPref?.isEnabled = false
            randomStartPref?.isEnabled = true
        } else {
            shortVideosPref?.isEnabled = true
            longVideosPref?.isEnabled = true
            randomStartPref?.isEnabled = false
        }
    }

    private fun setupSummaryUpdater(
        control: String,
        entries: Int,
    ) {
        val pref = findPreference<ListPreference>(control)
        pref?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                updateSummary(control, entries, pref.findIndexOfValue(newValue as String))
                true
            }
        pref?.findIndexOfValue(pref.value)?.let { updateSummary(control, entries, it) }
    }

    private fun updateSummary(
        control: String,
        entries: Int,
        index: Int,
    ) {
        val res = requireContext().resources
        val pref = findPreference<Preference>(control)
        val summaries = res?.getStringArray(entries)
        val summary = summaries?.elementAtOrNull(index) ?: ""
        pref?.summary = summary
    }
}
