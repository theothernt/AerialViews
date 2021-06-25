package com.codingbuffalo.aerialdream.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.codingbuffalo.aerialdream.models.LocalVideoFilter
import com.codingbuffalo.aerialdream.models.prefs.LocalVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.utils.FileHelper

class LocalVideoProvider(context: Context, private val prefs: LocalVideoPrefs) : VideoProvider(context) {

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
            if (prefs.filenameAsLocation) {
                val location = FileHelper.filenameToTitleCase(uri.lastPathSegment!!)
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

