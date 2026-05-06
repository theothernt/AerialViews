package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.utils.FirebaseHelper

class DpadRemoteFragment : MenuStateFragment() {
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_dpadremote, rootKey)
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("D-Pad/Remote", this)
    }
}
