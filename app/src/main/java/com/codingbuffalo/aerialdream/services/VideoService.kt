package com.codingbuffalo.aerialdream.services

import android.content.Context
import android.net.Uri
import android.util.Log
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.models.prefs.AppleVideoPrefs
import com.codingbuffalo.aerialdream.models.prefs.GeneralPrefs
import com.codingbuffalo.aerialdream.models.prefs.LocalVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.providers.AppleVideoProvider
import com.codingbuffalo.aerialdream.providers.LocalVideoProvider
import com.codingbuffalo.aerialdream.providers.VideoProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoService(context: Context) {
    private val providers = mutableListOf<VideoProvider>()

    init {
        if (AppleVideoPrefs.enabled)
            providers.add(AppleVideoProvider(context, AppleVideoPrefs))

        if (LocalVideoPrefs.enabled)
            providers.add(LocalVideoProvider(context, LocalVideoPrefs))
    }

    suspend fun fetchVideos(): VideoPlaylist = withContext(Dispatchers.IO) {
        var videos = mutableListOf<AerialVideo>()

        providers.forEach {
            videos.addAll(it.fetchVideos())
        }

        if (videos.isEmpty()) {
            Log.i(TAG, "No videos, adding empty one")
            videos.add(AerialVideo(Uri.parse(""), ""))
        }

        if (GeneralPrefs.removeDuplicates) {
            var numVideos = videos.size
            // Remove duplicates based on full path
            videos = videos.distinctBy { it.uri.toString().lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Vids removed based on full path: ${numVideos - videos.size}")

            numVideos = videos.size
            // Remove duplicates based on filename only
            videos = videos.distinctBy { it.uri.lastPathSegment?.lowercase() } as MutableList<AerialVideo>
            Log.i(TAG, "Vids removed based on filename: ${numVideos - videos.size}")
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