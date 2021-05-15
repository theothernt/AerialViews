package com.codingbuffalo.aerialdream.providers

import android.content.Context
import com.codingbuffalo.aerialdream.models.videos.AerialVideo

abstract class VideoProvider (val context: Context) {
    abstract fun fetchVideos(): List<AerialVideo>
}