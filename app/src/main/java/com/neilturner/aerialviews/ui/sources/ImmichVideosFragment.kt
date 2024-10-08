package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.immich.Album
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.providers.ImmichMediaProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class ImmichVideosFragment :
    PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var authTypePreference: ListPreference
    private lateinit var hostnamePreference: EditTextPreference
    private lateinit var schemePreference: ListPreference
    private lateinit var pathnamePreference: EditTextPreference
    private lateinit var passwordPreference: EditTextPreference
    private lateinit var apiKeyPreference: EditTextPreference
    private lateinit var selectAlbumPreference: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.sources_immich_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        authTypePreference = findPreference("immich_media_auth_type")!!
        hostnamePreference = findPreference("immich_media_hostname")!!
        schemePreference = findPreference("immich_media_scheme")!!
        pathnamePreference = findPreference("immich_media_pathname")!!
        passwordPreference = findPreference("immich_media_password")!!
        apiKeyPreference = findPreference("immich_media_api_key")!!
        selectAlbumPreference = findPreference("immich_media_select_album")!!

        limitTextInput()
        updateAuthTypeVisibility()
        updateSummary()

        setupPreferenceClickListeners()
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        updateSummary()
        if (key == "immich_media_auth_type") {
            updateAuthTypeVisibility()
        }
    }

    private fun setupPreferenceClickListeners() {
        findPreference<Preference>("immich_media_test_connection")?.setOnPreferenceClickListener {
            lifecycleScope.launch { testImmichConnection() }
            true
        }

        selectAlbumPreference.setOnPreferenceClickListener {
            lifecycleScope.launch { selectAlbum() }
            true
        }
    }

    private fun updateSummary() {
        updateHostnameSummary()
        updatePathnameSummary()
        updatePasswordSummary()
        updateApiKeySummary()
        updateSelectedAlbumSummary()
    }

    private fun updateHostnameSummary() {
        hostnamePreference.summary = if (hostnamePreference.text.isNullOrEmpty()) {
            getString(R.string.immich_media_hostname_summary)
        } else {
            hostnamePreference.text
        }
    }

    private fun updatePathnameSummary() {
        pathnamePreference.summary = if (pathnamePreference.text.isNullOrEmpty()) {
            getString(R.string.immich_media_pathname_summary)
        } else {
            pathnamePreference.text
        }
    }

    private fun updatePasswordSummary() {
        passwordPreference.summary = if (passwordPreference.text.isNullOrEmpty()) {
            getString(R.string.immich_media_password_summary)
        } else {
            "*".repeat(passwordPreference.text!!.length)
        }
    }

    private fun updateApiKeySummary() {
        apiKeyPreference.summary = if (apiKeyPreference.text.isNullOrEmpty()) {
            getString(R.string.immich_media_api_key_summary)
        } else {
            "*".repeat(apiKeyPreference.text!!.length)
        }
    }

    private fun updateSelectedAlbumSummary() {
        selectAlbumPreference.summary = if (ImmichMediaPrefs.selectedAlbumId.isEmpty()) {
            getString(R.string.immich_media_select_album_summary)
        } else {
            getString(R.string.immich_media_selected_album, ImmichMediaPrefs.selectedAlbumName)
        }
    }

    private fun updateAuthTypeVisibility() {
        val authType = ImmichAuthType.valueOf(authTypePreference.value)
        when (authType) {
            ImmichAuthType.SHARED_LINK -> {
                pathnamePreference.isVisible = true
                passwordPreference.isVisible = true
                apiKeyPreference.isVisible = false
                selectAlbumPreference.isVisible = false
            }
            ImmichAuthType.API_KEY -> {
                pathnamePreference.isVisible = false
                passwordPreference.isVisible = false
                apiKeyPreference.isVisible = true
                selectAlbumPreference.isVisible = true
            }
        }
    }

    private fun limitTextInput() {
        listOf(
            "immich_media_hostname",
            "immich_media_pathname",
            "immich_media_password",
            "immich_media_api_key"
        ).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener { it.setSingleLine() }
        }
    }

    private suspend fun testImmichConnection() = withContext(Dispatchers.IO) {
        val provider = ImmichMediaProvider(requireContext(), ImmichMediaPrefs)
        val result = provider.fetchTest()
        showDialog(getString(R.string.immich_media_test_results), result)
    }

    private suspend fun selectAlbum() {
        val provider = ImmichMediaProvider(requireContext(), ImmichMediaPrefs)
        provider.fetchAlbums().fold(
            onSuccess = { albums ->
                if (albums.isEmpty()) {
                    showErrorDialog(getString(R.string.immich_media_no_albums), getString(R.string.immich_media_no_albums_message))
                } else {
                    showAlbumSelectionDialog(albums)
                }
            },
            onFailure = { error ->
                showErrorDialog(getString(R.string.immich_media_fetch_albums_error), error.message ?: getString(R.string.immich_media_unknown_error))
            }
        )
    }

    private suspend fun showAlbumSelectionDialog(albums: List<Album>) = withContext(Dispatchers.Main) {
        if (albums.isEmpty()) {
            showErrorDialog(getString(R.string.immich_media_no_albums), getString(R.string.immich_media_no_albums_message))
            return@withContext
        }

        Timber.d("Showing album selection dialog with ${albums.size} albums")
        val albumNames = albums.map { "${it.name} (${it.assetCount} assets)" }.toTypedArray()
        AlertDialog.Builder(requireContext()).apply {
            setTitle(R.string.immich_media_select_album)
            setSingleChoiceItems(albumNames, -1) { dialog, which ->
                ImmichMediaPrefs.selectedAlbumId = albums[which].id
                ImmichMediaPrefs.selectedAlbumName = albums[which].name
                dialog.dismiss()
                updateSummary()
            }
            setNegativeButton(R.string.button_cancel, null)
            create().show()
        }
    }

    private suspend fun showErrorDialog(title: String, message: String) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.button_ok, null)
            .show()
    }

    private suspend fun showDialog(
        title: String,
        message: String,
    ) = withContext(Dispatchers.Main) {
        AlertDialog.Builder(requireContext()).apply {
            setTitle(title)
            setMessage(message)
            setPositiveButton(R.string.button_ok, null)
            create().show()
        }
    }
}