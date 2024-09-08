package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper

class OverlaysFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Overlays", this)
    }
}
