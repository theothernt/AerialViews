package com.neilturner.aerialviews.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.neilturner.aerialviews.models.LocalVideoFilter
import com.neilturner.aerialviews.models.prefs.AnyVideoPrefs
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.utils.FileHelper

class LocalVideoProvider(context: Context, private val prefs: AnyVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()
        val filter = prefs.filter
        val localVideos = FileHelper.findAllMedia(context)

        for (video in localVideos) {
            val filterFolder = "/${filter.name.lowercase()}/"

            val containsFolder = video
                    ?.lowercase()
                    ?.contains(filterFolder)

            if (filter != LocalVideoFilter.ALL &&
                    !containsFolder!!) {
                continue
            }

            val uri = Uri.parse(video)
            val filename = uri.lastPathSegment!!

            if (!FileHelper.isVideoFilename(filename))
            {
                Log.i(TAG, "Probably not a video: $filename")
                continue
            }

            if (prefs.filenameAsLocation) {
                val location = FileHelper.filenameToTitleCase(filename)
                videos.add(AerialVideo(uri, location))
            }  else {
                videos.add(AerialVideo(uri, ""))
            }
        }

        Log.i(TAG, "filter: ${filter}, videos found: ${videos.size}")
        return videos
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}

