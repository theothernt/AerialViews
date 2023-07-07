package com.neilturner.aerialviews.models.videos

import android.net.Uri
import com.neilturner.aerialviews.models.VideoQuality

class Comm2Video : AbstractVideo() {

    override fun uriAtQuality(quality: VideoQuality): Uri {
        val url = when (quality) {
            VideoQuality.VIDEO_1080_SDR -> video1080hdr // Map HDR to SDR as 10bit colour space alone is not HDR
            VideoQuality.VIDEO_4K_SDR -> video4ksdr
            else -> video1080h264
        }
        return Uri.parse(
            url
        )
    }

    override fun allUris(): List<Uri> {
        val uris = mutableSetOf<Uri>()
        enumValues<VideoQuality>().forEach { quality ->
            uriAtQuality(quality).let { uri -> uris.add(uri) }
        }
        return uris.toList()
    }
}
