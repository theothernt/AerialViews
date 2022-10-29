package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.VideoQuality

class Apple2018Video : AbstractVideo() {

    override fun uri(quality: VideoQuality): Uri? {
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
