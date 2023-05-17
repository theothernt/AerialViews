@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R

class AppearanceFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_appearance, rootKey)
    }
}
