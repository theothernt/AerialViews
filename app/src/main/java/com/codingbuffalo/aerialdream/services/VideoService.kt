package com.codingbuffalo.aerialdream.services

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.models.VideoSource
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.providers.Apple2019Provider
import com.codingbuffalo.aerialdream.providers.VideoProvider
import com.codingbuffalo.aerialdream.utils.FileHelper
import java.util.*

class VideoService(context: Context, prefs: SharedPreferences) {

    private val repositories: MutableList<VideoProvider> = LinkedList()

    init {

        // get all provider prefs
        // if enabled, load provider, pass prefs + context

        repositories.add(Apple2019Provider(context, prefs))
    }

    fun fetchVideos(): VideoPlaylist {

        // for each video provider ...

        val videos = emptyList<AerialVideo>() //buildVideoList()

        return VideoPlaylist(videos.toMutableList())
    }
}