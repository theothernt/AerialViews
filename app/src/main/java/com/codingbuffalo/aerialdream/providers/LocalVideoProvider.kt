package com.codingbuffalo.aerialdream.providers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.utils.FileHelper
import java.util.*

class LocalVideoProvider(context: Context) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        // get prefs
        // Aerial folder or all

        val videos = mutableListOf<AerialVideo>()
        val filter = VideoFolder.ALL

        val localVideos = FileHelper.findAllMedia(context)

        for (video in localVideos) {

            val containsFolder = video
                    ?.toLowerCase(Locale.ROOT)
                    ?.contains(filter.name.toLowerCase(Locale.ROOT))!!

            if (filter != VideoFolder.ALL &&
                    !containsFolder) {
                continue
            }

            // use filename as location?
            videos.add(AerialVideo(Uri.parse(video), ""))

        }

        //return videos
        return videos
    }
}

enum class VideoFolder {
    ALL,
    AERIAL
}