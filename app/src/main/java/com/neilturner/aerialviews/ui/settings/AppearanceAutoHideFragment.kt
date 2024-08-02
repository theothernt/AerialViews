package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.LoggingHelper

class AppearanceAutoHideFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_autohide, rootKey)
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("AutoHide", TAG)
    }

    companion object {
        private const val TAG = "AutoHideFragment"
    }
}
