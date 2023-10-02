package com.neilturner.aerialviews.services

import android.content.Context
import android.util.Log
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.enums.FilenameAsLocation
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm1VideoPrefs
import com.neilturner.aerialviews.models.prefs.Comm2VideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.prefs.SambaVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata
import com.neilturner.aerialviews.providers.AppleVideoProvider
import com.neilturner.aerialviews.providers.Comm1VideoProvider
import com.neilturner.aerialviews.providers.Comm2VideoProvider
import com.neilturner.aerialviews.providers.LocalVideoProvider
import com.neilturner.aerialviews.providers.SambaVideoProvider
import com.neilturner.aerialviews.providers.VideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import com.neilturner.aerialviews.utils.filename
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoService(val context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        providers.add(LocalVideoProvider(context, LocalVideoPrefs))
        providers.add(SambaVideoProvider(context, SambaVideoPrefs))

        // Prefer local videos first
        // Remote videos added last so they'll be filtered out if duplicates are found
        providers.add(Comm1VideoProvider(context, Comm1VideoPrefs))
        providers.add(Comm2VideoProvider(context, Comm2VideoPrefs))
        providers.add(AppleVideoProvider(context, AppleVideoPrefs))
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        var videos = mutableListOf<AerialVideo>()

        // Find all videos from all providers/sources
        providers.forEach {
            try {
                if (it.enabled) {
                    videos.addAll(it.fetchVideos())
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while fetching videos", ex)
            }
        }

        // Remove duplicates based on filename only
        if (GeneralPrefs.removeDuplicates) {
            val numVideos = videos.size
            videos = videos.distinctBy { it.uri.filename.lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Duplicate videos removed based on filename: ${numVideos - videos.size}")
        }

        // Add metadata to videos for filtering matched and unmatched
        val result = addMetadataToVideos(videos, providers)
        videos = result.first.toMutableList()

        // Add unmatched videos
        if (!GeneralPrefs.ignoreNonManifestVideos) {
            // Add filename as video location
            if (GeneralPrefs.filenameAsLocation == FilenameAsLocation.FORMATTED) {
                result.second.forEach { video ->
                    if (video.location.isBlank()) {
                        video.location = FileHelper.filenameToTitleCase(video.uri)
                    }
                }
            }
            if (GeneralPrefs.filenameAsLocation == FilenameAsLocation.SIMPLE) {
                result.second.forEach { video ->
                    if (video.location.isBlank()) {
                        video.location = FileHelper.filenameToString(video.uri)
                    }
                }
            }
            videos.addAll(result.second)
        }

        // Removed unneeded location data
//        when (InterfacePrefs.locationStyle) {
//            LocationType.POI -> videos.forEach { video ->
//                video.location = ""
//            }
//
//            LocationType.TITLE -> videos.forEach { video ->
//                video.poi = emptyMap()
//            }
//
//            else -> videos.forEach { video ->
//                video.location = ""
//                video.poi = emptyMap()
//            }
//        }

        // Randomise video order
        if (GeneralPrefs.shuffleVideos) {
            videos.shuffle()
        }

        Log.i(TAG, "Total vids: ${videos.size}")
        VideoPlaylist(videos)
    }

    private fun addMetadataToVideos(videos: List<AerialVideo>, providers: List<VideoProvider>): Pair<List<AerialVideo>, List<AerialVideo>> {
        val metadata = mutableListOf<VideoMetadata>()
        val matched = mutableListOf<AerialVideo>()
        val unmatched = mutableListOf<AerialVideo>()

        providers.forEach {
            try {
                metadata.addAll((it.fetchMetadata()))
            } catch (ex: Exception) {
                Log.e(TAG, "Exception while fetching metadata", ex)
            }
        }

        // Find video id in metadata list
        videos.forEach video@{ video ->
            metadata.forEach { metadata ->
                if (metadata.urls.any { it.contains(video.uri.filename, true) }) {
                    video.location = metadata.location
                    video.poi = metadata.poi
                    matched.add(video)
                    return@video
                }
            }
            unmatched.add(video)
        }
        return Pair(matched, unmatched)
    }

    companion object {
        private const val TAG = "VideoService"
    }
}
