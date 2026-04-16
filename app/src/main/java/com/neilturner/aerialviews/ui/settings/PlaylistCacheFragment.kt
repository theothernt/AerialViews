package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.AerialDatabase
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

class PlaylistCacheFragment : MenuStateFragment() {
    private lateinit var cacheRepository: PlaylistCacheRepository

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_playlist_cache, rootKey)
        cacheRepository = PlaylistCacheRepository(requireContext())
    }

    override fun onResume() {
        super.onResume()
        FirebaseHelper.analyticsScreenView("PlaylistCache", this)
        setupPreferences()
        updateStatus()
    }

    private fun setupPreferences() {
        val clearCachePref = findPreference<Preference>("clear_playlist_cache")
        clearCachePref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            clearCache()
            true
        }
    }

    private fun clearCache() {
        lifecycleScope.launch {
            cacheRepository.clearCache()
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), R.string.playlist_cache_cleared, Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }
    }

    private fun updateStatus() {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) {
                try {
                    val db = AerialDatabase.getInstance(requireContext())
                    db.playlistCacheDao().getPlaylistState()
                } catch (e: Exception) {
                    null
                }
            }
            
            withContext(Dispatchers.Main) {
                val statusPref = findPreference<Preference>("playlist_cache_status")
                if (state != null) {
                    val statusText = getString(
                        R.string.playlist_cache_status_active, 
                        state.totalMediaItems, 
                        state.totalMusicTracks
                    )
                    statusPref?.summary = statusText
                } else {
                    statusPref?.summary = getString(R.string.playlist_cache_status_empty)
                }
            }
        }
    }
}
