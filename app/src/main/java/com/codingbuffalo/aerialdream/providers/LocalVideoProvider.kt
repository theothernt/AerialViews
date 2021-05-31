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
            if (LocalVideoPrefs.filenameAsLocation) {
                val location = filenameToTitleCase(uri.lastPathSegment!!)
                videos.add(AerialVideo(uri, location))
            }  else {
                videos.add(AerialVideo(uri, ""))
            }
        }

        Log.i(TAG, "filter: ${filter}, videos found: ${videos.size}")
        return videos
    }

    private fun filenameToTitleCase(filename: String): String {
        val index = filename.lastIndexOf(".")

        // some.video.mov -> some.video
        var location = filename.substring(0, index)

        // somevideo -> Somevideo
        // city-place_video -> City - Place Video
        // some.video -> Some Video
        location = location.replace("-",".-.")
        location = location.replace("_",".")
        return location.split(".").joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}

