package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.videos.SimpleVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata

abstract class VideoProvider(val context: Context) {
    abstract fun fetchVideos(): List<SimpleVideo>

    abstract fun fetchTest(): String

    abstract fun fetchMetadata(): List<VideoMetadata>
}
