package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.videos.AerialVideo

abstract class VideoProvider(val context: Context) {
    abstract fun fetchVideos(): List<AerialVideo>
}
