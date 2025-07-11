package com.neilturner.aerialviews.models.videos

import android.net.Uri
import androidx.core.net.toUri
import com.neilturner.aerialviews.models.enums.VideoQuality
import com.neilturner.aerialviews.utils.filenameWithoutExtension
import kotlinx.serialization.Serializable

@Serializable
class Apple2018Video : AbstractVideo() {
    override fun uriAtQuality(quality: VideoQuality?): Uri {
        val url =
            when (quality) {
                VideoQuality.VIDEO_1080_SDR -> video1080sdr
                VideoQuality.VIDEO_1080_HDR -> video1080hdr
                VideoQuality.VIDEO_4K_SDR -> video4ksdr
                VideoQuality.VIDEO_4K_HDR -> video4khdr
                else -> video1080h264
            }.toString()
        // Apple seems to be using an invalid certificate
        return url.replace("https://", "http://").toUri()
    }

    override fun allUrls(): List<String> {
        val urls = mutableSetOf<String>()
        enumValues<VideoQuality>().forEach { quality ->
            uriAtQuality(quality).let { uri -> urls.add(uri.filenameWithoutExtension.lowercase()) }
        }
        return urls.toList()
    }
}

@Serializable
class Apple2018Videos {
    val assets: List<Apple2018Video>? = null
}
