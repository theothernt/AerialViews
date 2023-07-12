package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.R
import com.neilturner.aerialviews.models.LocationType
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.VideoQuality
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.InterfacePrefs
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.models.videos.AbstractVideo
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.providers.AppleVideoProvider
import com.neilturner.aerialviews.providers.Comm1VideoProvider
import com.neilturner.aerialviews.providers.Comm2VideoProvider
import com.neilturner.aerialviews.providers.LocalVideoProvider
import com.neilturner.aerialviews.providers.SambaVideoProvider
import com.neilturner.aerialviews.providers.VideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.JsonHelper
import com.neilturner.aerialviews.utils.filename
import com.neilturner.aerialviews.utils.toStringOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoService(private val context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        if (LocalVideoPrefs.enabled) {
            providers.add(LocalVideoProvider(context, LocalVideoPrefs))
        }

        if (SambaVideoPrefs.enabled) {
            providers.add(SambaVideoProvider(context, SambaVideoPrefs))
        }

        if (Comm1VideoPrefs.enabled) {
            providers.add(Comm1VideoProvider(context, Comm1VideoPrefs))
        }

        if (Comm2VideoPrefs.enabled) {
            providers.add(Comm2VideoProvider(context, Comm2VideoPrefs))
        }

        // Remote videos added last so they'll be filtered out if duplicates are found
        if (AppleVideoPrefs.enabled) {
            providers.add(AppleVideoProvider(context, AppleVideoPrefs))
        }
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        var aerialVideos = mutableListOf<AerialVideo>()
        val videoMetadata = mutableListOf<VideoMetadata>()

        // Find all videos from all providers/sources
        providers.forEach {
            try {
                aerialVideos.addAll(it.fetchVideos())
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while fetching videos", ex)
            }
        }

        // Remove duplicates based on filename only
        if (GeneralPrefs.removeDuplicates) {
            val numVideos = aerialVideos.size
            aerialVideos = aerialVideos.distinctBy { it.uri.filename.lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Duplicate videos removed based on filename: ${numVideos - aerialVideos.size}")
        }

        // Try to add location/POIs to all videos
        if (InterfacePrefs.locationStyle != LocationType.OFF) {
            providers.forEach {
                try {
                    videoMetadata.addAll((it.fetchMetadata()))
                } catch (ex: Exception) {
                    Log.e(TAG, "Exception while fetching metadata", ex)
                }
            }

            // Compare video id with metadata list
            aerialVideos.forEach { video ->
                videoMetadata.forEach metadata@{ metadata ->
                    if (metadata.urls.any { it.contains(video.uri.filename, true) }) {
                        video.location = metadata.location
                        video.poi = metadata.poi
                        return@metadata
                    }
                }
            }
        }

        // If there are videos with no location yet, use filename as location
//        if (!GeneralPrefs.ignoreNonManifestVideos &&
//            InterfacePrefs.locationStyle != LocationType.OFF &&
//            GeneralPrefs.filenameAsLocation
//        ) {
//            videos.forEach { video ->
//                if (video.location.isBlank()) {
//                    val location = FileHelper.filenameToTitleCase(video.uri)
//                    video.location = location
//                }
//            }
//        }
//

        // Randomise video order
        if (aerialVideos.isNotEmpty() && GeneralPrefs.shuffleVideos) {
            aerialVideos.shuffle()
        }

        // Log.i(TAG, "Total vids: ${videos.size}")
        VideoPlaylist(aerialVideos)
    }

    companion object {
        private const val TAG = "VideoService"
    }
}
