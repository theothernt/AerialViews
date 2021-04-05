package com.codingbuffalo.aerialdream.models.videos

import android.net.Uri
import com.codingbuffalo.aerialdream.models.VideoQuality
import com.google.gson.annotations.SerializedName

class Apple2019Video {

    @SerializedName("url-1080-H264")
    private val video_1080_h264: String? = null

    @SerializedName("url-1080-SDR")
    private val video_1080_sdr: String? = null

    @SerializedName("url-1080-HDR")
    private val video_1080_hdr: String? = null

    @SerializedName("url-4K-SDR")
    private val video_4k_sdr: String? = null

    @SerializedName("url-4K-HDR")
    private val video_4k_hdr: String? = null

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
            VideoQuality.VIDEO_1080_SDR -> video_1080_sdr
            VideoQuality.VIDEO_1080_HDR -> video_1080_hdr
            VideoQuality.VIDEO_4K_SDR -> video_4k_sdr
            VideoQuality.VIDEO_4K_HDR -> video_4k_hdr
            else -> video_1080_h264
        }
    }
}