package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
    private lateinit var selectAlbumsPreference: Preference
    private lateinit var pathnamePreference: EditTextPreference
    private lateinit var includeFavoritesPreference: Preference
    private lateinit var includeRatedPreference: Preference
    private lateinit var includeLimitPreference: Preference
    private lateinit var includeRecentPreference: Preference

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
        includeLimitPreference = findPreference("immich_media_include_random")!!
        includeRecentPreference = findPreference("immich_media_include_recent")!!

        lifecycleScope.launch {
            limitTextInput()
            updateAuthTypeVisibility()
            updateSummary()
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
        if (key == "immich_media_auth_type") {
            updateAuthTypeVisibility()
        }

        updateSummary()
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
            lifecycleScope.launch { pickAlbums() }
            true
        }
    }

    private fun updateSummary() {
        // Server URL
        val url = urlPreference.text
        if (!url.isNullOrEmpty()) {
            if (url.endsWith("/")) {
                val newUrl = url.dropLast(1)
                urlPreference.text = newUrl
                urlPreference.summary = newUrl
            } else {
                urlPreference.summary = url
            }
        } else {
            urlPreference.summary = getString(R.string.immich_media_url_summary)
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

        // Include Ratings
        includeRatedPreference.summary =
            if (ImmichMediaPrefs.includeRatings.isEmpty()) {
                "No rated photos selected"
            } else {
                val selectedRatings = ImmichMediaPrefs.includeRatings.sorted().joinToString(", ") { "$it★" }
                "$selectedRatings photos selected"
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
                includeLimitPreference.isVisible = false
                includeRecentPreference.isVisible = false
            }

            ImmichAuthType.API_KEY -> {
                pathnamePreference.isVisible = false
                passwordPreference.isVisible = false
                apiKeyPreference.isVisible = true
                selectAlbumsPreference.isVisible = true
                includeFavoritesPreference.isVisible = true
                includeRatedPreference.isVisible = true
                includeLimitPreference.isVisible = true
                includeRecentPreference.isVisible = true
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

    private suspend fun pickAlbums() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        if (ImmichMediaPrefs.url.isNotEmpty() && ImmichMediaPrefs.apiKey.isNotEmpty()) {
            val provider = ImmichMediaProvider(requireContext(), ImmichMediaPrefs)
            provider.fetchAlbums().fold(
                onSuccess = { albums ->
                    progressDialog.dismiss()
                    showAlbumMultiSelectDialog(albums)
                },
                onFailure = { exception ->
                    Timber.e(exception, "Failed to load albums for selection")
                    progressDialog.dismiss()
                    DialogHelper.show(
                        requireContext(),
                        "Error",
                        "Failed to load albums: ${exception.message}",
                    )
                },
            )
        } else {
            progressDialog.dismiss()
            DialogHelper.show(
                requireContext(),
                "Configuration Required",
                "Please configure server URL and API key first.",
            )
        }
    }

    private fun showAlbumMultiSelectDialog(albums: List<Album>) {
        if (albums.isEmpty()) {
            DialogHelper.show(
                requireContext(),
                "No Albums",
                "No albums found in your Immich instance.",
            )
            return
        }

        val albumNames = albums.map { "${it.name} (${it.assetCount} assets)" }.toTypedArray()
        val albumIds = albums.map { it.id }.toTypedArray()
        val currentSelectedAlbumIds = ImmichMediaPrefs.selectedAlbumIds
        val tempSelectedAlbumIds = currentSelectedAlbumIds.toMutableSet()
        val checkedItems =
            BooleanArray(albums.size) { index ->
                currentSelectedAlbumIds.contains(albumIds[index])
            }

        AlertDialog
            .Builder(requireContext())
            .setTitle("Select Albums")
            .setMultiChoiceItems(albumNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    tempSelectedAlbumIds.add(albumIds[which])
                } else {
                    tempSelectedAlbumIds.remove(albumIds[which])
                }
            }.setPositiveButton("OK") { _, _ ->
                ImmichMediaPrefs.selectedAlbumIds.clear()
                ImmichMediaPrefs.selectedAlbumIds.addAll(tempSelectedAlbumIds)
                updateSummary()
            }.setNegativeButton("Cancel", null)
            .create()
            .show()
    }
}
