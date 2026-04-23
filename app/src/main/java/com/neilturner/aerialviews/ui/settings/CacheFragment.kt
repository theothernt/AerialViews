package com.neilturner.aerialviews.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.data.AerialDatabase
import com.neilturner.aerialviews.data.PlaylistCacheRepository
import com.neilturner.aerialviews.data.PlaylistStateEntity
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.utils.FirebaseHelper
import com.neilturner.aerialviews.utils.MenuStateFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CacheFragment : MenuStateFragment() {
    private lateinit var cacheRepository: PlaylistCacheRepository

    override fun onCreatePreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        setPreferencesFromResource(R.xml.settings_cache, rootKey)
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
        clearCachePref?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                showClearCacheConfirmation()
                true
            }
    }

    private fun showClearCacheConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.playlist_cache_clear_title)
            .setMessage(R.string.playlist_cache_clear_confirmation)
            .setPositiveButton(R.string.button_ok) { _, _ ->
                clearCache()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun clearCache() {
        lifecycleScope.launch {
            try {
                cacheRepository.clearCache()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), R.string.playlist_cache_cleared, Toast.LENGTH_SHORT).show()
                    updateStatus()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    updateStatus()
                }
            }
        }
    }

    private fun updateStatus() {
        lifecycleScope.launch {
            val state =
                withContext(Dispatchers.IO) {
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
                    val statusText = buildStatusText(state)
                    statusPref?.summary = statusText
                } else {
                    statusPref?.summary = getString(R.string.playlist_cache_status_empty)
                }
            }
        }
    }

    private fun buildStatusText(state: PlaylistStateEntity): String {
        val baseText = getString(
            R.string.playlist_cache_status_active,
            state.totalMediaItems,
            state.totalMusicTracks,
        )

        val indexText = getString(R.string.playlist_cache_status_index, state.mediaPosition + 1) // 1-based for display

        val refreshIntervalStr = GeneralPrefs.playlistCacheRefreshInterval
        val intervalWeeks = refreshIntervalStr.toIntOrNull() ?: -1

        return when {
            intervalWeeks == -1 -> {
                // No time-based expiry (until end of playlist)
                "$baseText $indexText"
            }
            else -> {
                // Time-based expiry
                val cacheAgeMs = System.currentTimeMillis() - state.cachedAt
                val maxAgeMs = intervalWeeks * 7L * 24L * 60L * 60L * 1000L
                val remainingMs = maxAgeMs - cacheAgeMs
                
                if (remainingMs <= 0) {
                    // Cache has expired
                    val expiredText = getString(R.string.playlist_cache_expired)
                    "$baseText $indexText $expiredText"
                } else {
                    // Format remaining time
                    val remainingDays = TimeUnit.MILLISECONDS.toDays(remainingMs)
                    val remainingHours = TimeUnit.MILLISECONDS.toHours(remainingMs) % 24
                    val remainingMinutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs) % 60

                    val expiresText = getString(R.string.playlist_cache_expires)
                    val timeText = when {
                        remainingDays > 0 -> {
                            if (remainingHours > 0) {
                                "$expiresText " + getString(R.string.playlist_cache_status_expiry_d_h, remainingDays, remainingHours)
                            } else {
                                "$expiresText " + getString(R.string.playlist_cache_status_expiry_d, remainingDays)
                            }
                        }
                        remainingHours > 0 -> {
                            if (remainingMinutes > 0) {
                                "$expiresText " + getString(R.string.playlist_cache_status_expiry_h_m, remainingHours, remainingMinutes)
                            } else {
                                "$expiresText " + getString(R.string.playlist_cache_status_expiry_h, remainingHours)
                            }
                        }
                        else -> "$expiresText " + getString(R.string.playlist_cache_status_expiry_m, remainingMinutes)
                    }

                    "$baseText $indexText\n$timeText"
                }
            }
        }
    }
}
