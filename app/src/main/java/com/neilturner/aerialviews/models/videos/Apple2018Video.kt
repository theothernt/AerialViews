package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.utils.filename

class Apple2018Video : AbstractVideo() {

    override fun uriAtQuality(quality: VideoQuality): Uri {
        val url = when (quality) {
            VideoQuality.VIDEO_1080_SDR -> video1080sdr
            VideoQuality.VIDEO_1080_HDR -> video1080hdr
            VideoQuality.VIDEO_4K_SDR -> video4ksdr
            VideoQuality.VIDEO_4K_HDR -> video4khdr
            else -> video1080h264
        }
        return Uri.parse(
            url // Apple seems to be using an invalid certificate
                ?.replace("https://", "http://")
        )
    }

    override fun allUrls(): List<String> {
        val urls = mutableSetOf<String>()
        enumValues<VideoQuality>().forEach { quality ->
            uriAtQuality(quality).let { uri -> urls.add(uri.filename) }
        }
        return urls.toList()
    }
}
