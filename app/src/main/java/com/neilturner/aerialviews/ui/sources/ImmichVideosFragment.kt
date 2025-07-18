package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.enums.ImmichAuthType
import com.neilturner.aerialviews.models.prefs.ImmichMediaPrefs
import com.neilturner.aerialviews.providers.immich.Album
import com.neilturner.aerialviews.providers.immich.ImmichMediaProvider
import com.neilturner.aerialviews.utils.DialogHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import com.neilturner.aerialviews.utils.UrlParser
import kotlinx.coroutines.launch
import timber.log.Timber

class ImmichVideosFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var urlPreference: EditTextPreference
    private lateinit var authTypePreference: ListPreference
    private lateinit var validateSslPreference: Preference
    private lateinit var passwordPreference: EditTextPreference
    private lateinit var apiKeyPreference: EditTextPreference
    private lateinit var selectAlbumsPreference: MultiSelectListPreference
    private lateinit var pathnamePreference: EditTextPreference
    private lateinit var includeFavoritesPreference: Preference
    private lateinit var includeRatedPreference: Preference
    private var availableAlbums: List<Album> = emptyList()

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_immich_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        urlPreference = findPreference("immich_media_url")!!
        authTypePreference = findPreference("immich_media_auth_type")!!
        validateSslPreference = findPreference("immich_media_validate_ssl")!!
        passwordPreference = findPreference("immich_media_password")!!
        apiKeyPreference = findPreference("immich_media_api_key")!!
        selectAlbumsPreference = findPreference("immich_media_selected_album_ids")!!
        pathnamePreference = findPreference("immich_media_pathname")!!
        includeFavoritesPreference = findPreference("immich_media_include_favorites")!!
        includeRatedPreference = findPreference("immich_media_include_ratings")!!

        lifecycleScope.launch {
            limitTextInput()
            updateAuthTypeVisibility()
            updateSummary()
            loadAlbumsForPreference()
            setupPreferenceClickListeners()
        }
    }

    override fun onDestroy() {
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        updateSummary()
        if (key == "immich_media_auth_type") {
            updateAuthTypeVisibility()
        }
    }

    private fun setupPreferenceClickListeners() {
        urlPreference.setOnPreferenceChangeListener { _, newValue ->
            try {
                UrlParser.parseServerUrl(newValue.toString())
                true
            } catch (e: IllegalArgumentException) {
                AlertDialog
                    .Builder(requireContext())
                    .setMessage(getString(R.string.immich_media_url_invalid))
                    .setPositiveButton(R.string.button_ok, null)
                    .show()
                false
            }
        }

        findPreference<Preference>("immich_media_test_connection")?.setOnPreferenceClickListener {
            lifecycleScope.launch { testImmichConnection() }
            true
        }

        selectAlbumsPreference.setOnPreferenceClickListener {
            lifecycleScope.launch { loadAlbumsForPreference() }
            true
        }
    }

    private fun updateSummary() {
        // Server URL
        urlPreference.summary =
            if (urlPreference.text.isNullOrEmpty()) {
                getString(R.string.immich_media_url_summary)
            } else {
                urlPreference.text
            }

        // Shared Link Password
        passwordPreference.summary =
            if (passwordPreference.text.isNullOrEmpty()) {
                getString(R.string.immich_media_password_summary)
            } else {
                "*".repeat(passwordPreference.text!!.length)
            }

        // API Key
        apiKeyPreference.summary =
            if (apiKeyPreference.text.isNullOrEmpty()) {
                getString(R.string.immich_media_api_key_summary)
            } else {
                "*".repeat(apiKeyPreference.text!!.length)
            }

        updateSelectedAlbumsSummary()
    }

    private fun updateSelectedAlbumsSummary() {
        // Selected Albums
        selectAlbumsPreference.summary =
            if (ImmichMediaPrefs.selectedAlbumIds.isEmpty()) {
                getString(R.string.immich_media_select_albums_summary)
            } else {
                getString(
                    R.string.immich_media_selected_albums,
                    ImmichMediaPrefs.selectedAlbumIds.size,
                )
            }
    }

    private fun updateAuthTypeVisibility() {
        val authType = ImmichAuthType.valueOf(authTypePreference.value)
        when (authType) {
            ImmichAuthType.SHARED_LINK -> {
                pathnamePreference.isVisible = true
                passwordPreference.isVisible = true
                apiKeyPreference.isVisible = false
                selectAlbumsPreference.isVisible = false
                includeFavoritesPreference.isVisible = false
                includeRatedPreference.isVisible = false
            }
            ImmichAuthType.API_KEY -> {
                pathnamePreference.isVisible = false
                passwordPreference.isVisible = false
                apiKeyPreference.isVisible = true
                selectAlbumsPreference.isVisible = true
                includeFavoritesPreference.isVisible = true
                includeRatedPreference.isVisible = true
            }
        }
    }

    private fun limitTextInput() {
        listOf(
            "immich_media_url",
            "immich_media_password",
            "immich_media_api_key",
        ).forEach { key ->
            findPreference<EditTextPreference>(key)?.setOnBindEditTextListener { it.setSingleLine() }
        }
    }

    private suspend fun testImmichConnection() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val provider = ImmichMediaProvider(requireContext(), ImmichMediaPrefs)
        val result = provider.fetchTest()

        progressDialog.dismiss()
        DialogHelper.showOnMain(requireContext(), getString(R.string.immich_media_test_results), result)
    }

    private suspend fun loadAlbumsForPreference() {
        if (ImmichMediaPrefs.url.isNotEmpty() && ImmichMediaPrefs.apiKey.isNotEmpty()) {
            val provider = ImmichMediaProvider(requireContext(), ImmichMediaPrefs)
            provider.fetchAlbums().fold(
                onSuccess = { albums ->
                    availableAlbums = albums
                    populateAlbumsPreference(albums)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to load albums for preference")
                    // Clear preference entries if loading fails
                    selectAlbumsPreference.entries = emptyArray()
                    selectAlbumsPreference.entryValues = emptyArray()
                },
            )
        }
    }

    private fun populateAlbumsPreference(albums: List<Album>) {
        val albumNames = albums.map { "${it.name} (${it.assetCount} assets)" }.toTypedArray()
        val albumIds = albums.map { it.id }.toTypedArray()

        selectAlbumsPreference.entries = albumNames
        selectAlbumsPreference.entryValues = albumIds

        // Set the current selected values
        selectAlbumsPreference.values = ImmichMediaPrefs.selectedAlbumIds

        // Update summary after setting entries
        updateSelectedAlbumsSummary()
    }
}
