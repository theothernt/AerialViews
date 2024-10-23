package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.view.View
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        coroutineScope.launch {
            //delay(3000)
            val pref = findPreference<Preference>("settings")!!
            scrollToPreference(pref)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPause() {
        Timber.i("onPause")
        super.onPause()
    }

    override fun onResume() {
        Timber.i("onResume")
        FirebaseHelper.logScreenView("Settings", this)
        super.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        Timber.i("onSaveInstanceState")
        super.onSaveInstanceState(outState)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            Timber.i("onViewStateRestored - savedInstanceState")
        } else {
            Timber.i("onViewStateRestored")
        }
        super.onViewStateRestored(savedInstanceState)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        return super.onPreferenceTreeClick(preference)
    }
}
