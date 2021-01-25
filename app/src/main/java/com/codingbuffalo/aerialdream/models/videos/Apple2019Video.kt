package com.codingbuffalo.aerialdream.models.videos

import android.net.Uri
import com.google.gson.annotations.SerializedName

abstract class Apple2019Video : Video() {
    @SerializedName("url-1080-H264")
    private val url_1080_H264: String? = null

    @SerializedName("url-1080-SDR")
    private val url_1080_SDR: String? = null

    @SerializedName("url-1080-HDR")
    private val url_1080_HDR: String? = null

    @SerializedName("url-4K-SDR")
    private val url_4K_SDR: String? = null

    @SerializedName("url-4K-HDR")
    private val url_4K_HDR: String? = null

    override fun getUri(option: String): Uri? {
        return Uri.parse(
                getUrl(option) // Apple seems to be using an invalid certificate
                        ?.replace("https://", "http://")
        )
    }

    private fun getUrl(option: String): String? {
        return when (option) {
            "1080_sdr" -> url_1080_SDR
            "1080_hdr" -> url_1080_HDR
            "4k_sdr" -> url_4K_SDR
            "4k_hdr" -> url_4K_HDR
            else -> url_1080_H264
        }
    }
}