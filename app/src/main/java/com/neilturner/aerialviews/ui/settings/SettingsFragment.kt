package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import timber.log.Timber

class SettingsFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        try {
            val fav = ImmichMediaPrefs.includeFavorites
            Timber.d("Include Favorites: $fav")
        } catch (e: Exception) {
            Toast
                .makeText(
                    requireContext(),
                    "Error accessing Immich preferences: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Settings", this)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        return super.onPreferenceTreeClick(preference)
    }
}
