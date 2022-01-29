package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.FileHelper

class LocalVideoProvider(context: Context) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()
        val localVideos = FileHelper.findAllMedia(context)

        for (video in localVideos) {
            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment!!

            if (!FileHelper.isVideoFilename(filename)) {
                Log.i(TAG, "Probably not a video: $filename")
                continue
            }

            videos.add(AerialVideo(uri, ""))
        }

        Log.i(TAG, "videos found: ${videos.size}")
        return videos
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}
