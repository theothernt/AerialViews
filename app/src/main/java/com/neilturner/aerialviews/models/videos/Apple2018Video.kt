package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.google.gson.annotations.SerializedName
import com.neilturner.aerialviews.models.AppleVideoQuality

class Apple2018Video {

    @SerializedName("url-1080-H264")
    private val video1080h264: String? = null

    @SerializedName("url-1080-SDR")
    private val video1080sdr: String? = null

    @SerializedName("url-1080-HDR")
    private val video1080hdr: String? = null

    @SerializedName("url-4K-SDR")
    private val video4ksdr: String? = null

    @SerializedName("url-4K-HDR")
    private val video4khdr: String? = null

    private val accessibilityLabel: String? = null

    @SerializedName("pointsOfInterest")
    val pointsOfInterest: Map<Int, String> = emptyMap()

    val location: String
        get() = accessibilityLabel!!

    fun uri(quality: AppleVideoQuality): Uri? {
        return Uri.parse(
            url(quality) // Apple seems to be using an invalid certificate
                ?.replace("https://", "http://")
        )
    }

    private fun url(quality: AppleVideoQuality): String? {
        return when (quality) {
            AppleVideoQuality.VIDEO_1080_SDR -> video1080sdr
            AppleVideoQuality.VIDEO_1080_HDR -> video1080hdr
            AppleVideoQuality.VIDEO_4K_SDR -> video4ksdr
            AppleVideoQuality.VIDEO_4K_HDR -> video4khdr
            else -> video1080h264
        }
    }
}
