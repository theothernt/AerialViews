@file:Suppress("unused")

package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class Comm2VideosFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_comm2_videos, rootKey)
        updateSummaries()
    }

    private fun updateSummaries() {
        // Data usage
    }
}
