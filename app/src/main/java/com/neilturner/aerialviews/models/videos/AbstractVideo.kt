package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.utils.toStringOrEmpty

@Suppress("MemberVisibilityCanBePrivate")
abstract class AbstractVideo {
    @SerializedName("url-1080-H264")
    internal val video1080h264: String? = null

    @SerializedName("url-1080-SDR")
    internal val video1080sdr: String? = null

    @SerializedName("url-1080-HDR")
    internal val video1080hdr: String? = null

    @SerializedName("url-4K-SDR")
    internal val video4ksdr: String? = null

    @SerializedName("url-4K-HDR")
    internal val video4khdr: String? = null

    internal val accessibilityLabel: String? = null

    @SerializedName("pointsOfInterest")
    val pointsOfInterest: Map<Int, String> = emptyMap()

    val location: String
        get() = accessibilityLabel.toStringOrEmpty()

    abstract fun uriAtQuality(quality: VideoQuality): Uri

    abstract fun allUrls(): List<String>
}
