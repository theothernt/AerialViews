import android.Manifest
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.SearchType
import com.neilturner.aerialviews.models.prefs.MediaSelection
import com.neilturner.aerialviews.models.prefs.ProjectivyLocalMediaPrefs
import com.neilturner.aerialviews.providers.LocalMediaProvider
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.utils.DeviceHelper
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.PermissionHelper
import com.neilturner.aerialviews.utils.setSummaryFromValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectivyLocalVideosFragment :
    MenuStateFragment(),
    PreferenceManager.OnPreferenceTreeClickListener,
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var requestMultiplePermissions: ActivityResultLauncher<Array<String>>
    private lateinit var requestAudioPermission: ActivityResultLauncher<String>

    // Track previous selection to detect when music is added
    private var previousMediaSelection: Set<String> = ProjectivyLocalMediaPrefs.mediaSelection

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
                // If permission isn't granted, MediaStore scans will return no items.
                // Keep the UI as-is; user can switch to Folder access instead.
                PermissionHelper.isVideoImagePermissionGranted(permissions)
            }

        requestAudioPermission =
            registerForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (!granted) {
                    removeMusicFromSelection()
                }
            }

        lifecycleScope.launch {
            limitTextInput()
            showNvidiaShieldNoticeIfNeeded()
            enableMediaStoreOptions()
            updateMediaSelectionSummary()
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
        // Detect when music is added to the selection and request audio permission
        if (key == "projectivy_local_media_selection") {
            val current = ProjectivyLocalMediaPrefs.mediaSelection
            val addedMusic = MediaSelection.MUSIC in current && MediaSelection.MUSIC !in previousMediaSelection
            if (addedMusic && !PermissionHelper.hasAudioReadPermission(requireContext())) {
                requestAudioPermissionForMusic()
            }
            previousMediaSelection = current
        }

        updateMediaSelectionSummary()
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

    private fun updateMediaSelectionSummary() {
        preferenceScreen
            .findPreference<MultiSelectListPreference>("projectivy_local_media_selection")
            ?.setSummaryFromValues(ProjectivyLocalMediaPrefs.mediaSelection)
    }

    private suspend fun testLocalVideosFilter() =
        withContext(Dispatchers.IO) {
            val provider = LocalMediaProvider(requireContext(), ProjectivyLocalMediaPrefs)
            val result = provider.fetch()
            val message = when (result) {
                is ProviderFetchResult.Success -> result.summary
                is ProviderFetchResult.Error -> result.message
            }
            ensureActive()
            DialogHelper.showOnMain(requireContext(), resources.getString(R.string.local_videos_test_results), message)
        }

    private fun checkForMediaPermission() {
        if (PermissionHelper.hasVideoImagePermission(requireContext())) {
            return
        }

        requestMultiplePermissions.launch(PermissionHelper.getReadMediaPermissions())
    }

    private fun requestAudioPermissionForMusic() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    // Kotpref's stringSetPref is read-only (val), so we modify SharedPreferences directly.
    private fun removeMusicFromSelection() {
        val current = ProjectivyLocalMediaPrefs.mediaSelection
        if (MediaSelection.MUSIC in current) {
            val updated = (current - MediaSelection.MUSIC).toMutableSet()
            ProjectivyLocalMediaPrefs.preferences.edit().putStringSet("projectivy_local_media_selection", updated).apply()
            updateMediaSelectionSummary()
        }
    }

    private fun showNvidiaShieldNoticeIfNeeded() {
        if (!DeviceHelper.isNvidiaShield()) {
            return
        }
        val notice = findPreference<Preference>("projectivy_local_videos_shield_notice")
        notice?.isVisible = true
    }
}
