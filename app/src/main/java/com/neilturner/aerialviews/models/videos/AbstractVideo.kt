package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.neilturner.aerialviews.models.VideoQuality
import com.neilturner.aerialviews.utils.toStringOrEmpty

@Suppress("MemberVisibilityCanBePrivate", "MemberVisibilityCanBePrivate")
abstract class AbstractVideo {
    @SerializedName("url-1080-H264")
    protected val video1080h264: String? = null

    @SerializedName("url-1080-SDR")
    protected val video1080sdr: String? = null

    @SerializedName("url-1080-HDR")
    protected val video1080hdr: String? = null

    @SerializedName("url-4K-SDR")
    protected val video4ksdr: String? = null

    @SerializedName("url-4K-HDR")
    protected val video4khdr: String? = null

    val accessibilityLabel: String? = null

    @SerializedName("pointsOfInterest")
    val pointsOfInterest: Map<Int, String> = emptyMap()

    val location: String
        get() = accessibilityLabel.toStringOrEmpty()

    abstract fun uri(quality: VideoQuality): Uri?
}
