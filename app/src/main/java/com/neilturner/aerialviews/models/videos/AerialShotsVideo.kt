package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.neilturner.aerialviews.models.VideoQuality
import com.neilturner.aerialviews.utils.toStringOrEmpty

class AerialShotsVideo {

    @SerializedName("url-1080-H264")
    private val video1080h264: String? = null

    @SerializedName("url-1080-HDR") // 10bit colour space is not HDR?
    private val video1080sdr: String? = null

    @SerializedName("url-4K-SDR")
    private val video4ksdr: String? = null

    private val accessibilityLabel: String? = null

    @SerializedName("pointsOfInterest")
    val pointsOfInterest: Map<Int, String> = emptyMap()

    val location: String
        get() = accessibilityLabel.toStringOrEmpty()

    fun uri(quality: VideoQuality): Uri? {
        return Uri.parse(
            url(quality)
        )
    }

    private fun url(quality: VideoQuality): String? {
        return when (quality) {
            VideoQuality.VIDEO_1080_SDR -> video1080sdr
            VideoQuality.VIDEO_4K_SDR -> video4ksdr
            else -> video1080h264
        }
    }
}
