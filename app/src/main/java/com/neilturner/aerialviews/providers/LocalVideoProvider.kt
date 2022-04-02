package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.models.prefs.LocalVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.FileHelper

class LocalVideoProvider(context: Context, private val prefs: LocalVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)
        var filtered = 0

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment!!

            if (!FileHelper.isVideoFilename(filename)) {
                Log.i(TAG, "Probably not a video: $filename")
                continue
            }

            if (prefs.filter_enabled && shouldFilter(uri)) {
                filtered++
                continue
            }

            videos.add(AerialVideo(uri, ""))
        }

        Log.i(TAG, "Videos found: ${localVideos.size}")
        Log.i(TAG, "Videos removed by filter: $filtered")
        Log.i(TAG, "Videos selected for playback: ${localVideos.size - filtered}")

        return videos
    }

    private fun shouldFilter(uri: Uri): Boolean {
        val pathSegments = uri.pathSegments.dropLast(1) // x/y/z.mp4
        return !pathSegments.last().contains(prefs.filter_folder_name, true) // x/y
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
