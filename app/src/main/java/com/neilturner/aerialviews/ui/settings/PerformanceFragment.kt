@file:Suppress("unused")

package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.os.Parcelable
import com.neilturner.aerialviews.R
import androidx.preference.PreferenceFragmentCompat

class PerformanceFragment : PreferenceFragmentCompat() {
    private var state: Parcelable? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_performance, rootKey)
    }

    override fun onPause() {
        state = listView.layoutManager?.onSaveInstanceState()
        super.onPause()
    }

    override fun onResume() {
        if (state != null) listView.layoutManager?.onRestoreInstanceState(state)
        super.onResume()
    }
}