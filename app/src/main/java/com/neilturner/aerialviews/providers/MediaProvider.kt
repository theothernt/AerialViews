package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.videos.AerialVideo
import com.neilturner.aerialviews.models.videos.VideoMetadata

abstract class MediaProvider(val context: Context) {

    abstract val enabled: Boolean

    abstract fun fetchVideos(): List<AerialVideo>

    abstract fun fetchTest(): String

    abstract fun fetchMetadata(): List<VideoMetadata>

    // type
    // VideoType.LOCAL, REMOTE
    // LOCAL/LAN vs REMOVE/HTTPS/WEBDAV
}
