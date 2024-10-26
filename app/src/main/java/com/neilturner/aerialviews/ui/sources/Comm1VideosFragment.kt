package com.neilturner.aerialviews.ui.sources

import android.os.Bundle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.MenuStateFragment

class Comm1VideosFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_comm1_videos, rootKey)
    }
}
