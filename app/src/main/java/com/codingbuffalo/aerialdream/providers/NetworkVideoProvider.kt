package com.codingbuffalo.aerialdream.providers

import android.content.Context
import android.content.SharedPreferences
import com.codingbuffalo.aerialdream.models.videos.AerialVideo

class NetworkVideoProvider(context: Context) : VideoProvider(context) {

    override fun fetchVideos(): List<AerialVideo> {
        // get prefs

        // check network
        // check share access

        // get video file list (mp4, mov, mkv?)

        return emptyList()
    }
}