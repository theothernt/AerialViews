package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.utils.FirebaseHelper

class AppearanceFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_appearance, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("Appearance", this)
    }
}
