package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.ui.preferences.HorizontalSlotsPreference
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment

class OverlaysRowTopFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    
    private var horizontalSlotsPreference: HorizontalSlotsPreference? = null
    
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_row_top, rootKey)
        horizontalSlotsPreference = findPreference("horizontal_slots_preference")
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Top Row", this)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        // The custom preference handles its own updates now
        // This method can be used for other preference changes if needed
    }
}