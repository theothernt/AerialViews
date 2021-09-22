package com.neilturner.aerialviews.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.models.VideoPlaylist
import com.neilturner.aerialviews.models.prefs.AppleVideoPrefs
import com.neilturner.aerialviews.models.prefs.GeneralPrefs
import com.neilturner.aerialviews.models.prefs.AnyVideoPrefs
import com.neilturner.aerialviews.models.prefs.NetworkVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.providers.AppleVideoProvider
import com.neilturner.aerialviews.providers.LocalVideoProvider
import com.neilturner.aerialviews.providers.NetworkVideoProvider
import com.neilturner.aerialviews.providers.VideoProvider
import com.neilturner.aerialviews.utils.FileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoService(context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        if (AppleVideoPrefs.enabled)
            providers.add(AppleVideoProvider(context, AppleVideoPrefs))

        if (AnyVideoPrefs.enabled)
            providers.add(LocalVideoProvider(context, AnyVideoPrefs))

        if (NetworkVideoPrefs.enabled)
            providers.add(NetworkVideoProvider(context, NetworkVideoPrefs))
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        var videos = mutableListOf<AerialVideo>()

        // Find all videos from all providers/sources
        providers.forEach {
            val newVideos = try {
                it.fetchVideos()
            } catch(ex: Exception) {
                Log.e(TAG, ex.message!!)
                emptyList()
            }
            videos.addAll(newVideos)
        }

        if (GeneralPrefs.removeDuplicates) {
            var numVideos = videos.size
            // Remove duplicates based on full path
            videos = videos.distinctBy { it.uri.toString().lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Videos removed based on full path: ${numVideos - videos.size}")

            numVideos = videos.size
            // Remove duplicates based on filename only
            videos = videos.distinctBy { it.uri.lastPathSegment?.lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Videos removed based on filename: ${numVideos - videos.size}")
        }

        // Try and add locations by looking up video filenames in various manifests
        if (AnyVideoPrefs.useAppleManifests) {

            if (AnyVideoPrefs.ignoreNonManifestVideos) { }
        }

        if (AnyVideoPrefs.useCustomManifests) {

            if (AnyVideoPrefs.ignoreNonManifestVideos) { }
        }

        // If there are still no locations, use filename as location
        if (AnyVideoPrefs.filenameAsLocation) {
            videos.forEach { video ->
                if (video.location.isBlank()) {
                    val filename = video.uri.lastPathSegment!!
                    val location = FileHelper.filenameToTitleCase(filename)
                    video.location = location
                }
            }
        }

        if (videos.isEmpty()) {
            Log.i(TAG, "No videos, adding empty one")
            videos.add(AerialVideo(Uri.parse(""), ""))
        }

        if (GeneralPrefs.shuffleVideos)
            videos.shuffle()

        Log.i(TAG, "Total vids: ${videos.size}")
        VideoPlaylist(videos)
    }

    companion object {
        private const val TAG = "VideoService"
    }
}