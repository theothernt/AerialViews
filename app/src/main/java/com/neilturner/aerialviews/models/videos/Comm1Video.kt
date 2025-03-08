package com.neilturner.aerialviews.models.videos

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.utils.filenameWithoutExtension

class Comm1Video : AbstractVideo() {
    override fun uriAtQuality(quality: VideoQuality?): Uri {
        val url =
            when (quality) {
                VideoQuality.VIDEO_1080_SDR -> video1080sdr
                VideoQuality.VIDEO_4K_SDR -> video4ksdr
                else -> video1080h264
            }.toString()
        return url.toUri()
    }

    override fun allUrls(): List<String> {
        val urls = mutableSetOf<String>()
        enumValues<VideoQuality>().forEach { quality ->
            uriAtQuality(quality).let { uri -> urls.add(uri.filenameWithoutExtension.lowercase()) }
        }
        return urls.toList()
    }
}
