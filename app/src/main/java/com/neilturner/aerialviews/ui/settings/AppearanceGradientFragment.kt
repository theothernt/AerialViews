package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.LoggingHelper

class AppearanceGradientFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance_gradient, rootKey)
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Gradient", TAG)
    }

    companion object {
        private const val TAG = "GradientFragment"
    }
}
