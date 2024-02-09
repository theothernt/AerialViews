package com.neilturner.aerialviews.providers

import android.content.Context
import com.neilturner.aerialviews.models.videos.AerialMedia
import com.neilturner.aerialviews.models.videos.VideoMetadata

abstract class MediaProvider(val context: Context) {

    abstract val enabled: Boolean

    abstract fun fetchMedia(): List<AerialMedia>

    abstract fun fetchTest(): String

    abstract fun fetchMetadata(): List<VideoMetadata>

    // type
    // VideoType.LOCAL, REMOTE
    // LOCAL/LAN vs REMOVE/HTTPS/WEBDAV
}
