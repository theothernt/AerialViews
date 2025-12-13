package com.neilturner.aerialviews.ui.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.ProjectivyLocalMediaPrefs
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectivyLocalVideosFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var requestMultiplePermissions: ActivityResultLauncher<Array<String>>

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_projectivy_local_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        // Projectivy Local/USB integration is MediaStore-only (no legacy filesystem mode).
        ProjectivyLocalMediaPrefs.searchType = SearchType.MEDIA_STORE

        requestMultiplePermissions =
            registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { permissions ->
                // If permission isn’t granted, MediaStore scans will return no items.
                // Keep the UI as-is; user can switch to Folder access instead.
                PermissionHelper.isReadMediaPermissionGranted(permissions)
            }

        lifecycleScope.launch {
            limitTextInput()
            showNvidiaShieldNoticeIfNeeded()
            enableMediaStoreOptions()
        }

        checkForMediaPermission()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key.isNullOrEmpty()) {
            return super.onPreferenceTreeClick(preference)
        }

        if (preference.key.contains("projectivy_local_videos_search_test")) {
            lifecycleScope.launch { testLocalVideosFilter() }
            return true
        }

        return super.onPreferenceTreeClick(preference)
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        // No dynamic enable/disable for legacy options (removed).
    }

    private fun enableMediaStoreOptions() {
        // Many preferences depend on this notice preference. Keep it enabled so dependents stay enabled.
        preferenceScreen.findPreference<Preference>("projectivy_local_videos_media_store_notice")?.isEnabled = true
    }

    private fun limitTextInput() {
        preferenceScreen
            .findPreference<EditTextPreference>("projectivy_local_videos_media_store_filter_folder")
            ?.setOnBindEditTextListener { it.setSingleLine() }
    }

    private suspend fun testLocalVideosFilter() =
        withContext(Dispatchers.IO) {
            val provider = LocalMediaProvider(requireContext(), ProjectivyLocalMediaPrefs)
            val result = provider.fetchTest()
            ensureActive()
            DialogHelper.showOnMain(requireContext(), resources.getString(R.string.local_videos_test_results), result)
        }

    private fun checkForMediaPermission() {
        if (PermissionHelper.hasMediaReadPermission(requireContext())) {
            return
        }

        requestMultiplePermissions.launch(PermissionHelper.getReadMediaPermissions())
    }

    private fun showNvidiaShieldNoticeIfNeeded() {
        if (!DeviceHelper.isNvidiaShield()) {
            return
        }
        val notice = findPreference<Preference>("projectivy_local_videos_shield_notice")
        notice?.isVisible = true
    }
}
