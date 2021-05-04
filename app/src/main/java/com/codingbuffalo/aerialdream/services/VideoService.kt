package com.codingbuffalo.aerialdream.services

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.models.VideoSource
import com.codingbuffalo.aerialdream.models.prefs.Apple2019Prefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.providers.Apple2019Provider
import com.codingbuffalo.aerialdream.providers.LocalVideoProvider
import com.codingbuffalo.aerialdream.providers.VideoProvider
import com.codingbuffalo.aerialdream.utils.FileHelper
import java.util.*

class VideoService(context: Context) {

    private val providers = mutableListOf<VideoProvider>()

    init {
        // get all provider prefs
        // if enabled, load provider, pass prefs + context

        providers.add(Apple2019Provider(context, Apple2019Prefs))
        providers.add(LocalVideoProvider(context))
    }

    fun fetchVideos(): VideoPlaylist {
        val videos = mutableListOf<AerialVideo>()

        providers.forEach {
            videos.addAll(it.fetchVideos())
        }

        return VideoPlaylist(videos.toMutableList())
    }
}