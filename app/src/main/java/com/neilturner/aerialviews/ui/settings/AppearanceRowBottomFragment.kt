package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AppearanceRowBottomFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance_row_bottom, rootKey)
    }
}
