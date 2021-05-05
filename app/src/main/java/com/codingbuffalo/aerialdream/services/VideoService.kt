package com.codingbuffalo.aerialdream.services

import android.content.Context
import com.codingbuffalo.aerialdream.models.VideoPlaylist
import com.codingbuffalo.aerialdream.models.prefs.AppleVideoPrefs
import com.codingbuffalo.aerialdream.models.prefs.LocalVideoPrefs
import com.codingbuffalo.aerialdream.models.videos.AerialVideo
import com.codingbuffalo.aerialdream.providers.AppleVideoProvider
import com.codingbuffalo.aerialdream.providers.LocalVideoProvider
import com.codingbuffalo.aerialdream.providers.VideoProvider

class VideoService(context: Context) {

    private val providers = mutableListOf<VideoProvider>()

    init {

//        if (AppleVideoPrefs.enabled)
//            providers.add(AppleVideoProvider(context, AppleVideoPrefs))

        if (LocalVideoPrefs.enabled)
            providers.add(LocalVideoProvider(context, LocalVideoPrefs))
    }

    fun fetchVideos(): VideoPlaylist {
        val videos = mutableListOf<AerialVideo>()

        providers.forEach {
            videos.addAll(it.fetchVideos())
        }

        return VideoPlaylist(videos.toMutableList())
    }
}