@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings.sources

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AerialCommunityVideosFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_aerial_community_videos, rootKey)
    }
}
