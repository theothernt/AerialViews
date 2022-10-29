package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.VideoQuality

class Comm1Video : AbstractVideo() {

    override fun uri(quality: VideoQuality): Uri? {
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
