package com.neilturner.aerialviews.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.annotation.RequiresApi
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.utils.FirebaseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class SettingsFragment :
    PreferenceFragmentCompat(),
    PreferenceManager.OnPreferenceTreeClickListener {

    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var position = -1

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onPause() {
        Timber.i("onPause")

        val view = listView.findFocus()
        if (view != null) {
            position = listView.layoutManager?.getPosition(view) ?: -1
            Timber.i("onPause - position: $position")
        }

        super.onPause()
    }

    override fun onResume() {
        Timber.i("onResume")
        FirebaseHelper.logScreenView("Settings", this)

        coroutineScope.launch {
            delay(50)

            if (position != -1) {
                val item = listView.findViewHolderForAdapterPosition(position)?.itemView
                item?.requestFocus()
            }
        }

        super.onResume()
    }

    override fun onDestroy() {
        Timber.i("onDestroy")
        super.onDestroy()
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
