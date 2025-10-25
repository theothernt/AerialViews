package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.MenuStateFragment

class ProjectivyFragment : MenuStateFragment() {

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_projectivy, rootKey)
    }
}
