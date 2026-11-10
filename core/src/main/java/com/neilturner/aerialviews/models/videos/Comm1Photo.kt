package com.neilturner.aerialviews.models.videos

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.VideoQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Comm1Photo {
    @SerialName("url-1080")
    val url1080: String? = null

    @SerialName("url-4K")
    val url4k: String? = null

    val title: String = ""

    fun uriAtQuality(quality: VideoQuality?): Uri {
        val url =
            when (quality) {
                VideoQuality.VIDEO_4K_SDR, VideoQuality.VIDEO_4K_HDR -> url4k ?: url1080
                else -> url1080 ?: url4k
            }.orEmpty()
        return url.toUri()
    }
}
