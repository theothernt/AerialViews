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
        var excluded = 0
        var filtered = 0

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment!!

            if (!FileHelper.isVideoFilename(filename)) {
                // Log.i(TAG, "Probably not a video: $filename")
                excluded++
                continue
            }

            if (prefs.filter_enabled && FileHelper.shouldFilter(uri, LocalVideoPrefs.filter_folder_name)) {
                // Log.i(TAG, "Filtering out video: $filename")
                filtered++
                continue
            }

            videos.add(AerialVideo(uri, ""))
        }

        Log.i(TAG, "Videos found by Media Scanner: ${localVideos.size}")
        Log.i(TAG, "Videos with supported file extensions: ${localVideos.size - excluded}")
        Log.i(TAG, "Videos removed by filter: $filtered")
        Log.i(TAG, "Videos selected for playback: ${localVideos.size - (filtered + excluded)}")

        return videos
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
