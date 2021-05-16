package com.codingbuffalo.aerialdream.providers

import android.content.Context
import android.net.Uri
import android.util.Log
import com.codingbuffalo.aerialdream.models.LocalVideoFilter
import com.codingbuffalo.aerialdream.models.prefs.LocalVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.utils.FileHelper
import java.util.*

class LocalVideoProvider(context: Context, private val prefs: LocalVideoPrefs) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        val videos = mutableListOf<AerialVideo>()
        val filter = prefs.filter
        val localVideos = FileHelper.findAllMedia(context)

        for (video in localVideos) {
            val filterFolder = "/${filter.name.toLowerCase(Locale.ROOT)}/"

            val containsFolder = video
                    ?.toLowerCase(Locale.ROOT)
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

        // somevideo.mov -> somevideo
        val location = filename.substring(0, index)

        // somevideo -> Somevideo
        // some-video -> Some Video
        // some.video -> Some Video
        location.replace("-",".")
        location.replace("_",".")
        return location.split(".").joinToString(" ") { it.toLowerCase(Locale.ROOT).capitalize(Locale.ROOT) }
    }

    companion object {
        private const val TAG = "LocalVideoProvider"
    }
}

