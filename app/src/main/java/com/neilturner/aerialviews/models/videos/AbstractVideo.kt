package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.enums.VideoQuality
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
abstract class AbstractVideo {
    @SerialName("url-1080-H264")
    val video1080h264: String? = null

    @SerialName("url-1080-SDR")
    val video1080sdr: String? = null

    @SerialName("url-1080-HDR")
    val video1080hdr: String? = null

    @SerialName("url-4K-SDR")
    val video4ksdr: String? = null

    @SerialName("url-4K-HDR")
    val video4khdr: String? = null

    @SerialName("accessibilityLabel")
    val description: String = ""

    @SerialName("pointsOfInterest")
    val pointsOfInterest: Map<Int, String> = emptyMap()

    abstract fun uriAtQuality(quality: VideoQuality?): Uri

    abstract fun allUrls(): List<String>
}
