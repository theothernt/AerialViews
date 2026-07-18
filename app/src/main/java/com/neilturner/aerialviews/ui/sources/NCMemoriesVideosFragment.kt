package com.neilturner.aerialviews.ui.sources

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.network.UrlParser
import com.neilturner.aerialviews.models.prefs.NCMemoriesMediaPrefs
import com.neilturner.aerialviews.providers.ProviderFetchResult
import com.neilturner.aerialviews.providers.ncmemories.Album
import com.neilturner.aerialviews.providers.ncmemories.NCMemoriesMediaProvider
import com.neilturner.aerialviews.ui.controls.MenuStateFragment
import com.neilturner.aerialviews.ui.helpers.DialogHelper
import com.neilturner.aerialviews.utils.setSummaryFromValues
import kotlinx.coroutines.launch
import timber.log.Timber

class NCMemoriesVideosFragment :
    MenuStateFragment(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var urlPreference: EditTextPreference
    private lateinit var mediaSelectionPreference: MultiSelectListPreference
    private lateinit var validateSslPreference: Preference
    private lateinit var usernamePreference: EditTextPreference
    private lateinit var passwordPreference: EditTextPreference
    private lateinit var selectAlbumsPreference: Preference
    private lateinit var includeFavoritesPreference: Preference
    private lateinit var includeRecentPreference: Preference

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.sources_ncmemories_videos, rootKey)
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

        urlPreference = findPreference("ncmemories_media_url")!!
        mediaSelectionPreference = findPreference("ncmemories_media_selection")!!
        validateSslPreference = findPreference("ncmemories_media_validate_ssl")!!
        usernamePreference = findPreference("ncmemories_media_username")!!
        passwordPreference = findPreference("ncmemories_media_password")!!
        selectAlbumsPreference = findPreference("ncmemories_media_selected_album_ids")!!
        includeFavoritesPreference = findPreference("ncmemories_media_include_favorites")!!
        includeRecentPreference = findPreference("ncmemories_media_include_recent")!!

        lifecycleScope.launch {
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
        updateSummary()
    }

    private fun setupPreferenceClickListeners() {
        urlPreference.setOnPreferenceChangeListener { _, newValue ->
            try {
                UrlParser.parseServerUrl(newValue.toString())
                clearSelectedAlbumsIfChanged(urlPreference.text.orEmpty(), newValue.toString())
                true
            } catch (e: IllegalArgumentException) {
                AlertDialog
                    .Builder(requireContext())
                    .setMessage(getString(R.string.ncmemories_media_url_invalid))
                    .setPositiveButton(R.string.button_ok, null)
                    .show()
                false
            }
        }

        usernamePreference.setOnPreferenceChangeListener { _, newValue ->
            clearSelectedAlbumsIfChanged(usernamePreference.text.orEmpty(), newValue.toString())
            true
        }

        findPreference<Preference>("ncmemories_media_test_connection")?.setOnPreferenceClickListener {
            lifecycleScope.launch { testNCMemoriesConnection() }
            true
        }

        selectAlbumsPreference.setOnPreferenceClickListener {
            lifecycleScope.launch { pickAlbums() }
            true
        }
    }

    private fun clearSelectedAlbumsIfChanged(
        currentValue: String,
        newValue: String,
    ) {
        if (currentValue != newValue && NCMemoriesMediaPrefs.selectedAlbumIds.isNotEmpty()) {
            NCMemoriesMediaPrefs.selectedAlbumIds.clear()
        }
    }

    private fun updateSummary() {
        mediaSelectionPreference.setSummaryFromValues(NCMemoriesMediaPrefs.mediaSelection)

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
            urlPreference.summary = getString(R.string.ncmemories_media_url_summary)
        }

        // Username
        usernamePreference.summary =
            if (usernamePreference.text.isNullOrEmpty()) {
                getString(R.string.ncmemories_media_username_summary)
            } else {
                usernamePreference.text
            }

        // Password
        passwordPreference.summary =
            if (passwordPreference.text.isNullOrEmpty()) {
                getString(R.string.ncmemories_media_password_summary)
            } else {
                "*".repeat(passwordPreference.text!!.length)
            }

        // Selected Albums
        selectAlbumsPreference.summary =
            if (NCMemoriesMediaPrefs.selectedAlbumIds.isEmpty()) {
                getString(R.string.ncmemories_media_select_albums_summary)
            } else {
                getString(
                    R.string.ncmemories_media_selected_albums,
                    NCMemoriesMediaPrefs.selectedAlbumIds.size,
                )
            }
    }

    private suspend fun testNCMemoriesConnection() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        // skip EXIF queries for quick response
        NCMemoriesMediaPrefs.isTestConnection = true
        val provider = NCMemoriesMediaProvider(requireContext(), NCMemoriesMediaPrefs)
        val message =
            when (val result = provider.fetch()) {
                is ProviderFetchResult.Success -> result.summary
                is ProviderFetchResult.Error -> result.message
            }
        NCMemoriesMediaPrefs.isTestConnection = false

        progressDialog.dismiss()
        DialogHelper.showOnMain(
            requireContext(),
            getString(R.string.ncmemories_media_test_results),
            message
        )
    }

    private suspend fun pickAlbums() {
        val loadingMessage = getString(R.string.message_media_searching)
        val progressDialog =
            DialogHelper.progressDialog(
                requireContext(),
                loadingMessage,
            )
        progressDialog.show()

        val allCredentialsPresent = NCMemoriesMediaPrefs.url.isNotEmpty() &&
                NCMemoriesMediaPrefs.username.isNotEmpty() &&
                NCMemoriesMediaPrefs.password.isNotEmpty()

        if (allCredentialsPresent) {
            val provider = NCMemoriesMediaProvider(requireContext(), NCMemoriesMediaPrefs)
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
                        getString(R.string.ncmemories_media_fetch_albums_error) + ": ${exception.message}",
                    )
                },
            )
        } else {
            progressDialog.dismiss()
            DialogHelper.show(
                requireContext(),
                "Configuration Required",
                "Please configure server URL, username and password first.",
            )
        }
    }

    private fun showAlbumMultiSelectDialog(albums: List<Album>) {
        if (albums.isEmpty()) {
            DialogHelper.show(
                requireContext(),
                getString(R.string.ncmemories_media_no_albums),
                getString(R.string.ncmemories_media_no_albums_message),
            )
            return
        }

        val albumNames = albums.map { "${it.name} (${it.count} files)" }.toTypedArray()
        val albumIds = albums.map { it.albumId.toString() }.toTypedArray()
        val availableAlbumIds = albumIds.toSet()
        val currentSelectedAlbumIds =
            NCMemoriesMediaPrefs.selectedAlbumIds.intersect(availableAlbumIds)
        val tempSelectedAlbumIds = currentSelectedAlbumIds.toMutableSet()
        val checkedItems =
            BooleanArray(albums.size) { index ->
                currentSelectedAlbumIds.contains(albumIds[index])
            }

        AlertDialog
            .Builder(requireContext())
            .setTitle(getString(R.string.ncmemories_media_select_albums))
            .setMultiChoiceItems(albumNames, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    tempSelectedAlbumIds.add(albumIds[which])
                } else {
                    tempSelectedAlbumIds.remove(albumIds[which])
                }
            }.setPositiveButton("OK") { _, _ ->
                NCMemoriesMediaPrefs.selectedAlbumIds.clear()
                NCMemoriesMediaPrefs.selectedAlbumIds.addAll(tempSelectedAlbumIds)
                updateSummary()
            }.setNegativeButton("Cancel", null)
            .create()
            .show()
    }
}
