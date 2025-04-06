package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.ui.KtorServer
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.launch

class SettingsFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener {
    private lateinit var ktorServer: KtorServer

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings, rootKey)

        lifecycleScope.launch {
            ktorServer = KtorServer()
            ktorServer.start()
        }
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.logScreenView("Settings", this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ktorServer.stop()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        return super.onPreferenceTreeClick(preference)
    }
}
