package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.LoggingHelper

class OverlaysClockFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_overlays_clock, rootKey)
    }

    override fun onResume() {
        super.onResume()
        LoggingHelper.logScreenView("Clock", TAG)
    }

    companion object {
        private const val TAG = "ClockFragment"
    }
}
