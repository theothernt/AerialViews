package com.codingbuffalo.aerialdream.models.videos

import android.net.Uri
import com.codingbuffalo.aerialdream.models.VideoQuality
import com.google.gson.annotations.SerializedName

class Apple2019Video {

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

    val location: String
        get() = accessibilityLabel!!

    fun uri(quality: VideoQuality): Uri {
        return Uri.parse(
                url(quality) // Apple seems to be using an invalid certificate
                        ?.replace("https://", "http://")
        )
    }

    private fun url(quality: VideoQuality): String? {
        return when (quality) {
            VideoQuality.VIDEO_1080_SDR -> video1080sdr
            VideoQuality.VIDEO_1080_HDR -> video1080hdr
            VideoQuality.VIDEO_4K_SDR -> video4ksdr
            VideoQuality.VIDEO_4K_HDR -> video4khdr
            else -> video1080h264
        }
    }
}