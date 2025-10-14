package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper

class QuietHoursFragment : PreferenceFragmentCompat() {
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_quiet_hours, rootKey)
    }
    
    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Quiet Hours Settings", this)
    }
}
